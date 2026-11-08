# MMS provenance and license audit

## Decision

The exact local MMS ONNX artifacts are **not approved for iTantra distribution**. Their local integrity is recorded, but provenance is incomplete and every candidate official Facebook MMS source model declares CC-BY-NC-4.0. CC-BY-NC-4.0 does not grant commercial use, so it cannot satisfy iTantra's commercial redistribution requirement for APK, S3, or CloudFront distribution.

## What is and is not evidenced

- Local source of truth: `app/src/main/assets/models/manifest.json`; all listed local model/token SHA-256 values are present there.
- Local files are VITS ONNX opset-13 graphs with FP32 initializers; this proves format, not upstream authorization.
- Official candidate sources: `https://huggingface.co/facebook/mms-tts-<iso6393>`; each public model card metadata declares `cc-by-nc-4.0`.
- The repository has **no** checked-in conversion command, converter name/version/commit, conversion log, input checkpoint checksum, tokenizer acquisition record, or authorization to redistribute a derived ONNX artifact.
- `sherpa-onnx` is a runtime, not provenance for the converted weights. The pinned Android AAR is `1.13.6`, but it does not establish rights to model weights or local token files.

## Exact local artifact and candidate-upstream inventory

| iTantra | Local ONNX SHA-256 | Local bytes | Local token SHA-256 | Candidate official source revision | Candidate upstream safetensors SHA-256 | Model/card license | Conversion/token provenance | Shipping result |
|---|---|---:|---|---|---|---|---|---|
| en / `eng` | `e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254` | 114,016,948 | `dff08580748be688d9112d62d6352422c56d372dfe34b24ea3f66fa1b75cfaa9` | `c71de0fe7204c83f1c10820a7d696d0b450048ba` | `69cf8b651c1493f1801dfd2311c298d694a38357bc9a1e41f410491ea6f0e1be` | CC-BY-NC-4.0 | Unknown | Fail |
| hi / `hin` | `42c69b3611dc016ff337e994c78a76b5131156718c5a69e9cfa8912cfd850c5e` | 114,043,064 | `aa9abf8320da4ca80b153c51e2d3b6b52cb41e930ccc16dec570e70726ab3dd6` | `1d83b223ec78e30b944f7d96bd117eb3d7023303` | `675b45f0c34c5f7f8c78baf0403a6afb16a3e5fac0c2740601dad877d1f5cb0c` | CC-BY-NC-4.0 | Unknown | Fail |
| gu / `guj` | `59f073b2e63771dd7d7972c17577e05a903ae6f4aa8c65a6dc1d9eb1a9812ed2` | 114,033,848 | `2d855f2affb7586cc6be095a4382eb0bee2a22242deda32c86aab6b1a810d8c4` | `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` | `f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a` | CC-BY-NC-4.0 | Unknown | Fail |
| mr / `mar` | `03021bb722e8a19a44be6bb327a441c4061a0c75cbd565f3455e64c84713f03a` | 114,043,832 | `4d968029d0754b41633cb0871cce6796a5ab3d3bc2b9b91c5721cfdf85156083` | `7af4a6db1df2eb20042d24cc7c180a492df1cc13` | `fb53c1d8cd642b1df939162c71f91fb75d40b9c919a860de2f171e46295312b9` | CC-BY-NC-4.0 | Unknown | Fail |
| kn / `kan` | `8b6f313ebfdf423991ab6444a4520a72cc506a5b262a5d0c0c467ed7d8555834` | 114,045,368 | `a4a44037f7492c9e5b69a4483250e54af9a2d528bd4e19de95a21ff0c5e82a8a` | `30e3c5d533e8c559c10bf0d25637fea51b95bd7c` | `12a68748b7aeab553c8b145ab2de198617644eb89e5f0b7008a2f3a7cf91a9bd` | CC-BY-NC-4.0 | Unknown | Fail |
| ml / `mal` | `13965d4eeed2f5198c53689e192ce7095e167fb11dd0e72ae5cd8a3bf117374b` | 114,052,280 | `3a752c36593cc519193c8caa6c00371369b423366fa6ead8d91945a28b2c46a3` | `893b8c6442d6a630896d1d3ac0f429094ddfae82` | `a97a1e677ec67e05124b799dadd66630181fe9c29beb4e590454689ff8f698c5` | CC-BY-NC-4.0 | Unknown | Fail |
| ta / `tam` | `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` | 114,032,312 | `0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8` | `e9cf59dae34f0f51e3b1842876a658e4516f9fe4` | `29357e85c7f86f7725b6ff7bab78d1963899c16c2874da1db3a7d6b90f36b050` | CC-BY-NC-4.0 | Unknown | Fail |
| te / `tel` | `e82525ab1e662ba96ef81346ff1943c5bb731853c1f96d02fbff2e7a0284824c` | 114,037,688 | `528152cb4121272e6e71f7a1d91c8d4922b92fae414ae599db608cb92a738bb7` | `dea6807154acc01918581982dcd40a116882a14d` | `067ac7ad1632d214dec61bf78cd3c2921358284614f5a4063378cc1434a389cf` | CC-BY-NC-4.0 | Unknown | Fail |
| or / `ory` | `a90e1a48d4aa404c12504f47a89bbe310110a9d43dd2e093346f539efc8728a1` | 114,046,136 | `bc90e5423446ca730aef6b56a4656cb07421c7538429a50b9c396fe0758fd587` | `581f221219b728fab4d53efb24e18134bd1a9e28` | `0bdc5d4b35d9011422e66b0fd8b45f65aa1420c937978481b1eede1db5510c6b` | CC-BY-NC-4.0 | Unknown | Fail |
| bn / `ben` | `d16d6ea228104876499a8b09b3417e6de0aca91e1ff04d23f5a3fc2f6ddd3ad7` | 114,044,600 | `a74190ec42b8f0afb349276430c716f1b585090d8fe6d3c1d6d5fe510798a387` | `0da99de6074c8829121cdabfbdba423af18e8e56` | `6a0e055ec13ecd0a07ead04dec7974a071846e64a9fe0c0b188f61b32a9bd5ba` | CC-BY-NC-4.0 | Unknown | Fail |

Candidate upstream revisions are evidence of corresponding official language repositories, not proof that they are the exact source checkpoint for the local ONNX outputs. The distinct local and upstream file checksums confirm they are different artifacts.

## Required remediation before any MMS claim

A producer must supply a signed or otherwise verifiable chain for every local artifact: exact upstream URL/revision/checksum, converter command and source commit, converter license, tokenizer source/license/checksum, resulting ONNX checksum, and authorization for the derived artifact. Even that chain would not cure CC-BY-NC commercial-use restriction without a different permissive upstream artifact or separate rights.
