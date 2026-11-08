# IndicVoices-R official access resolution

## Official access path

1. Pinned repository: https://github.com/AI4Bharat/IndicVoices-R at `4d77d8a402b680fb739c1a6a3ec531de0cf17139`.
2. Pinned `data_links.txt` SHA-256: `d8c77ae455793da40597417b04f533d2c7109adab4ce478962ee542cd1ac1cb2`. It lists public-looking object URLs named `<Language>.tar.gz` under `https://indic-tts-public.objectstore.e2enetworks.net/data/ivr/`.
3. Zenodo concept record: https://zenodo.org/records/11636050. Its current version record is https://zenodo.org/records/14016558.

The repository README documents direct `wget` retrieval and does not document authentication, registration, signing, approval, or an alternate manifest endpoint.

## Zenodo result

Zenodo version record `14016558` is public and declares CC-BY-4.0. It contains exactly one file:

| File | Bytes | Official checksum | Local verified checksum | Contents |
|---|---:|---|---|---|
| `AI4Bharat/IndicVoices-R-v1.0.2.zip` | 310,314 | MD5 `92977e31e17f6728c8ed8c625c698be2` | SHA-256 `835d1ab9940449a795a78ca949099dff9d41c0183ace35431151416cf1168798` | repository snapshot, plots, license, README, `data_links.txt`; no audio/manifests |

The Zenodo archive references repository snapshot `78bb08115f0bbb3e8aac7da54c6047f26dc8a273`. It is an official software/instructions release, not the 1,704-hour dataset distribution.

## Object-store result

Each required target slice named by the pinned index — `Bengali.tar.gz`, `Gujarati.tar.gz`, `Hindi.tar.gz`, `Kannada.tar.gz`, `Malayalam.tar.gz`, `Marathi.tar.gz`, `Odia.tar.gz`, `Tamil.tar.gz`, and `Telugu.tar.gz` — returned HTTP `403 AccessDenied` with the requested key in the provider response. No official archive byte size, SHA-256, ETag, manifest, or authentication instruction was supplied.

This audit made one ordinary unauthenticated request per official URL. It did not retry with alternate headers, enumerate storage, use credentials, or seek unofficial copies.

## USER ACTION REQUIRED

Ask the official IndicVoices-R/AI4Bharat dataset provider to supply one of the following official channels:

1. restored public read access to the nine documented object URLs; or
2. an official authenticated/registration process and instructions; or
3. an official archive manifest containing immutable URLs, archive sizes, SHA-256 values, per-language metadata manifests, data-card/consent documentation, and redistribution terms.

Do not provide credentials to this repository or commit signed URLs. Until the provider supplies an official route, bulk acquisition is unsafe and training remains blocked.
