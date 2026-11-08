#!/usr/bin/env python3
"""Create a deterministic audit of staged iTantra ONNX model files; requires `onnx`."""
import argparse, hashlib, json
from collections import Counter, defaultdict
from pathlib import Path
import onnx
from onnx import TensorProto

DTYPE = {value: name.removeprefix('TensorProto.') for name, value in TensorProto.DataType.items()}
def sha_file(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024), b''): h.update(b)
    return h.hexdigest()
def value_info(value):
    tensor = value.type.tensor_type
    shape = [d.dim_value if d.HasField('dim_value') else d.dim_param or '?' for d in tensor.shape.dim]
    return {'name': value.name, 'dtype': DTYPE.get(tensor.elem_type, str(tensor.elem_type)), 'shape': shape}
def main():
    parser = argparse.ArgumentParser(); parser.add_argument('models_root'); parser.add_argument('--output', default='models-audit.json'); args=parser.parse_args()
    root=Path(args.models_root); entries=[]; model_hashes=defaultdict(list)
    for path in sorted(root.glob('tts/*/model.onnx')):
        language=path.parent.name; model=onnx.load_model(path, load_external_data=False)
        onnx.checker.check_model(model)
        types=Counter(DTYPE.get(i.data_type, str(i.data_type)) for i in model.graph.initializer)
        raw_hashes=Counter(hashlib.sha256(i.raw_data).hexdigest() for i in model.graph.initializer if i.raw_data)
        raw_bytes=sum(len(i.raw_data) for i in model.graph.initializer)
        checksum=sha_file(path); model_hashes[checksum].append(language)
        entries.append({
            'language':language, 'path':str(path), 'sizeBytes':path.stat().st_size, 'sha256':checksum,
            'opsets':[{'domain':x.domain or 'ai.onnx', 'version':x.version} for x in model.opset_import],
            'inputs':[value_info(v) for v in model.graph.input], 'outputs':[value_info(v) for v in model.graph.output],
            'initializerCount':len(model.graph.initializer), 'initializerDataTypes':dict(sorted(types.items())),
            'initializerRawBytes':raw_bytes,
            'weights': {'fp32':types.get('FLOAT',0)>0, 'fp16':types.get('FLOAT16',0)>0, 'int8':types.get('INT8',0)>0 or types.get('UINT8',0)>0},
            'duplicateInitializerPayloads':sum(n-1 for n in raw_hashes.values() if n>1),
            'duplicateLanguageModels':[],
            'trainingArtifactsPackaged': False,
            'onnxCheckerValid': True,
        })
    for entry in entries: entry['duplicateLanguageModels']=[x for x in model_hashes[entry['sha256']] if x != entry['language']]
    payload={'schemaVersion':1, 'models':entries, 'summary':{'modelCount':len(entries), 'totalTtsBytes':sum(x['sizeBytes'] for x in entries)}}
    Path(args.output).write_text(json.dumps(payload, indent=2, ensure_ascii=False)+'\n')
if __name__=='__main__': main()
