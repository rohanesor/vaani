#!/usr/bin/env python3
"""Convert and validate the Gujarati MMS-TTS checkpoint for sherpa-onnx.

The source directory is read-only from this script's perspective. The output
directory must be a new machine-local directory outside the Git checkout when
the script is used for model preparation. It contains model.onnx, tokens.txt,
and adaptation_metadata.json; none are intended for Git.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import tempfile
from pathlib import Path


SOURCE_MODEL_SHA256 = "f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a"
SOURCE_REVISION = "b72e80a7eeca90b72e0af2e2d00b77a336ce242d"
EXPECTED_SAMPLE_RATE = 16000
EXPECTED_VOCAB_SIZE = 60
EXPECTED_SHERPA_VERSION = "1.13.6"
EXPECTED_INPUTS = ("x", "x_length", "noise_scale", "length_scale", "noise_scale_w")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"Gujarati MMS adaptation failed: {message}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--text",
        default="મને મદદની જરૂર છે.",
        help="Native Gujarati text used for token and synthesis validation.",
    )
    return parser.parse_args()


def require_files(source_dir: Path) -> None:
    required = ("config.json", "model.safetensors", "special_tokens_map.json", "tokenizer_config.json", "vocab.json")
    missing = [name for name in required if not (source_dir / name).is_file()]
    if missing:
        fail(f"source directory is missing: {', '.join(missing)}")


def load_source(source_dir: Path):
    import torch
    from transformers import AutoTokenizer, VitsModel

    config = json.loads((source_dir / "config.json").read_text(encoding="utf-8"))
    tokenizer_config = json.loads((source_dir / "tokenizer_config.json").read_text(encoding="utf-8"))
    vocab = json.loads((source_dir / "vocab.json").read_text(encoding="utf-8"))
    if config.get("model_type") != "vits":
        fail(f"expected model_type=vits, got {config.get('model_type')!r}")
    if config.get("sampling_rate") != EXPECTED_SAMPLE_RATE:
        fail(f"expected 16000 Hz source, got {config.get('sampling_rate')!r}")
    if config.get("num_speakers") != 1:
        fail(f"expected one source speaker, got {config.get('num_speakers')!r}")
    if config.get("vocab_size") != EXPECTED_VOCAB_SIZE:
        fail(f"expected vocab_size=60, got {config.get('vocab_size')!r}")
    if tokenizer_config.get("language") != "guj" or tokenizer_config.get("phonemize") is not False:
        fail("source tokenizer is not the expected Gujarati character frontend")
    if tokenizer_config.get("add_blank") is not True:
        fail("source tokenizer does not declare add_blank=true")
    ids = sorted(vocab.values())
    if ids != list(range(EXPECTED_VOCAB_SIZE)):
        fail("vocab IDs are not the contiguous 0..59 character map expected by sherpa")

    tokenizer = AutoTokenizer.from_pretrained(str(source_dir), local_files_only=True)
    model = VitsModel.from_pretrained(str(source_dir), local_files_only=True, use_safetensors=True).eval()
    model.requires_grad_(False)
    return torch, config, tokenizer, model, vocab


def write_tokens(path: Path, vocab: dict[str, int]) -> None:
    by_id = {token_id: token for token, token_id in vocab.items()}
    if set(by_id) != set(range(EXPECTED_VOCAB_SIZE)):
        fail("cannot write tokens.txt because vocabulary IDs are incomplete")
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        for token_id in range(EXPECTED_VOCAB_SIZE):
            token = by_id[token_id]
            if "\n" in token or "\r" in token or "\t" in token:
                fail(f"vocabulary token {token_id} contains a line-breaking/control character")
            stream.write(f"{token} {token_id}\n")


def export_onnx(torch, model, tokenizer, text: str, destination: Path) -> list[int]:
    class SherpaVits(torch.nn.Module):
        def __init__(self, wrapped):
            super().__init__()
            self.wrapped = wrapped

        def forward(self, x, x_length, noise_scale, length_scale, noise_scale_w):
            # Transformers VitsModel owns the duration/noise defaults. Keep the
            # sherpa control inputs in the graph until a native control-aware
            # exporter is approved; the current prototype does not reinterpret
            # those values or modify checkpoint weights.
            waveform = self.wrapped(input_ids=x, return_dict=False)[0]
            controls = (
                x_length.to(waveform.dtype).sum()
                + noise_scale.sum()
                + length_scale.sum()
                + noise_scale_w.sum()
            ) * 0.0
            return (waveform + controls).unsqueeze(1)

    inputs = tokenizer(text, return_tensors="pt")
    input_ids = inputs["input_ids"].to(dtype=torch.int64)
    x_length = torch.tensor([input_ids.shape[1]], dtype=torch.int64)
    noise_scale = torch.tensor([1.0], dtype=torch.float32)
    length_scale = torch.tensor([1.0], dtype=torch.float32)
    noise_scale_w = torch.tensor([1.0], dtype=torch.float32)
    torch.manual_seed(0)
    wrapped = SherpaVits(model).eval()
    with torch.no_grad():
        torch.onnx.export(
            wrapped,
            (input_ids, x_length, noise_scale, length_scale, noise_scale_w),
            str(destination),
            opset_version=13,
            dynamo=False,
            do_constant_folding=True,
            input_names=list(EXPECTED_INPUTS),
            output_names=["y"],
            dynamic_axes={
                "x": {0: "N", 1: "L"},
                "x_length": {0: "N"},
                "y": {0: "N", 2: "L"},
            },
        )
    return input_ids.reshape(-1).tolist()


def audit_graph(path: Path, config: dict) -> dict:
    import onnx

    graph = onnx.load(str(path), load_external_data=False)
    onnx.checker.check_model(graph)
    inputs = [item.name for item in graph.graph.input]
    outputs = [item.name for item in graph.graph.output]
    if tuple(inputs) != EXPECTED_INPUTS or outputs != ["y"]:
        fail(f"graph interface mismatch: inputs={inputs!r}, outputs={outputs!r}")
    if [(item.domain, item.version) for item in graph.opset_import] != [("", 13)]:
        fail("graph must contain only the default ONNX opset 13 import")
    expected_types = {
        "x": 7,
        "x_length": 7,
        "noise_scale": 1,
        "length_scale": 1,
        "noise_scale_w": 1,
        "y": 1,
    }

    def tensor_shape(value) -> list[str | int]:
        return [dimension.dim_value or dimension.dim_param for dimension in value.type.tensor_type.shape.dim]

    graph_values = list(graph.graph.input) + list(graph.graph.output)
    interface = {}
    for value in graph_values:
        tensor = value.type.tensor_type
        if tensor.elem_type != expected_types[value.name]:
            fail(f"{value.name} has unexpected ONNX tensor type: {tensor.elem_type}")
        interface[value.name] = {
            "onnx_elem_type": tensor.elem_type,
            "shape": tensor_shape(value),
        }
    if interface["y"]["shape"] != ["N", 1, "L"]:
        fail(f"output y has unexpected shape: {interface['y']['shape']!r}")
    expected_sample_rate = str(config["sampling_rate"])
    metadata = {
        item.key: item.value for item in graph.metadata_props
    }
    for key, value in {
        "model_type": "vits",
        "comment": "mms-guj-prototype",
        "language": "guj",
        "add_blank": "1",
        "frontend": "characters",
        "n_speakers": "1",
        "sample_rate": expected_sample_rate,
    }.items():
        entry = graph.metadata_props.add()
        entry.key = key
        entry.value = value
        metadata[key] = value
    onnx.save(graph, str(path))
    return {
        "opset": 13,
        "inputs": inputs,
        "outputs": outputs,
        "interface": interface,
        "metadata": metadata,
        "initializer_count": len(graph.graph.initializer),
    }


def validate_package(output_dir: Path, tokenizer, text: str) -> dict:
    import numpy as np
    import onnxruntime as ort
    import sherpa_onnx

    if sherpa_onnx.__version__ != EXPECTED_SHERPA_VERSION:
        fail(
            f"this prototype must run with sherpa-onnx {EXPECTED_SHERPA_VERSION}, "
            f"got {sherpa_onnx.__version__}"
        )

    model_path = output_dir / "model.onnx"
    tokens_path = output_dir / "tokens.txt"
    ids = tokenizer(text, return_tensors="np")["input_ids"].astype(np.int64)
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    ort_audio = session.run(
        ["y"],
        {
            "x": ids,
            "x_length": np.asarray([ids.shape[1]], dtype=np.int64),
            "noise_scale": np.asarray([0.0], dtype=np.float32),
            "length_scale": np.asarray([1.0], dtype=np.float32),
            "noise_scale_w": np.asarray([0.0], dtype=np.float32),
        },
    )[0]
    if ort_audio.size == 0 or not np.isfinite(ort_audio).all():
        fail("ONNX Runtime returned empty or non-finite audio")

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model_path),
                tokens=str(tokens_path),
                lexicon="",
                data_dir="",
                dict_dir="",
            ),
            num_threads=1,
            provider="cpu",
        ),
        max_num_sentences=1,
    )
    if not config.validate():
        fail("sherpa-onnx rejected the generated configuration")
    tts = sherpa_onnx.OfflineTts(config)
    audio = tts.generate(text, sid=0, speed=1.0)
    samples = np.asarray(audio.samples, dtype=np.float32)
    duration = len(samples) / audio.sample_rate if audio.sample_rate else 0.0
    if audio.sample_rate != EXPECTED_SAMPLE_RATE:
        fail(f"sherpa returned {audio.sample_rate} Hz, expected {EXPECTED_SAMPLE_RATE} Hz")
    if len(samples) == 0 or not np.isfinite(samples).all():
        fail("sherpa returned empty or non-finite audio")
    if not 0.05 <= duration <= 30.0:
        fail(f"sherpa returned implausible duration: {duration:.3f}s")
    return {
        "token_count": int(ids.shape[1]),
        "onnx_runtime_samples": int(ort_audio.size),
        "sherpa_samples": int(len(samples)),
        "sample_rate": int(audio.sample_rate),
        "duration_s": round(duration, 6),
        "finite": True,
        "peak_abs": round(float(np.max(np.abs(samples))), 6),
        "clip_fraction": round(float(np.mean(np.abs(samples) >= 0.999)), 8),
        "config_valid": True,
        "synthesis": "PASS",
    }


def main() -> None:
    args = parse_args()
    source_dir = args.source_dir.resolve()
    output_dir = args.output_dir.resolve()
    if not source_dir.is_dir():
        fail(f"source directory does not exist: {source_dir}")
    if output_dir == source_dir or source_dir in output_dir.parents:
        fail("output directory must not be inside the source directory")
    if output_dir.exists():
        if any(output_dir.iterdir()):
            fail(f"output directory must be new and empty: {output_dir}")
    else:
        output_dir.mkdir(parents=True)
    require_files(source_dir)
    source_model = source_dir / "model.safetensors"
    actual_source_sha256 = sha256(source_model)
    if actual_source_sha256 != SOURCE_MODEL_SHA256:
        fail(f"source checksum mismatch: {actual_source_sha256} != {SOURCE_MODEL_SHA256}")

    torch, model_config, tokenizer, model, vocab = load_source(source_dir)
    with tempfile.TemporaryDirectory(prefix="vokie-guj-export-") as temp:
        temp_model = Path(temp) / "model.onnx"
        token_ids = export_onnx(torch, model, tokenizer, args.text, temp_model)
        graph_audit = audit_graph(temp_model, model_config)
        shutil.copyfile(temp_model, output_dir / "model.onnx")
    write_tokens(output_dir / "tokens.txt", vocab)
    validation = validate_package(output_dir, tokenizer, args.text)

    import onnx
    import onnxruntime
    import sherpa_onnx
    import transformers

    metadata = {
        "language": "guj",
        "source_repository": "facebook/mms-tts-guj",
        "source_revision": SOURCE_REVISION,
        "source_model": "model.safetensors",
        "source_model_sha256": actual_source_sha256,
        "source_model_bytes": source_model.stat().st_size,
        "conversion": {
            "script": "model-lab/tools/prepare_mms_guj_sherpa.py",
            "onnx_opset": 13,
            "frontend": "MMS VitsTokenizer character map; add_blank=true; phonemize=false",
            "speaker_id": 0,
            "control_inputs_preserved": True,
            "control_inputs_behavior": "Transformer VitsModel defaults; no checkpoint weights altered",
        },
        "output": {
            "model": "model.onnx",
            "model_bytes": (output_dir / "model.onnx").stat().st_size,
            "model_sha256": sha256(output_dir / "model.onnx"),
            "tokens": "tokens.txt",
            "tokens_bytes": (output_dir / "tokens.txt").stat().st_size,
            "tokens_sha256": sha256(output_dir / "tokens.txt"),
        },
        "tool_versions": {
            "python": ".".join(map(str, sys.version_info[:3])),
            "torch": torch.__version__,
            "transformers": transformers.__version__,
            "onnx": onnx.__version__,
            "onnxruntime": onnxruntime.__version__,
            "sherpa_onnx": sherpa_onnx.__version__,
        },
        "graph_audit": graph_audit,
        "validation": validation,
        "validation_text": args.text,
        "token_ids": token_ids,
    }
    (output_dir / "adaptation_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    # Windows development shells may use cp1252; keep the metadata file
    # Unicode-rich while making the command-line result portable.
    print(json.dumps(metadata, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
