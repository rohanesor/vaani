# Technical request for official IndicVoices-R access

This is a prepared technical request only. It must not be sent automatically and requires no credential in this repository.

## Requested official delivery information

For `Bengali.tar.gz`, `Gujarati.tar.gz`, `Hindi.tar.gz`, `Kannada.tar.gz`, `Malayalam.tar.gz`, `Marathi.tar.gz`, `Odia.tar.gz`, `Tamil.tar.gz`, and `Telugu.tar.gz`, please provide:

1. immutable official archive URL or object identifier;
2. archive byte size and SHA-256 (or clearly identified official equivalent checksum);
3. exact dataset release/revision and changelog;
4. per-language machine-readable manifest with source file ID/path, language, speaker ID, duration, sample rate, audio format, transcript, normalized transcript, and source archive;
5. data card describing collection, recording conditions, quality-control, known limitations, language/domain/speaker distributions, and intended use;
6. speaker consent/privacy and metadata handling terms;
7. audio, transcript, metadata, and archive redistribution terms;
8. commercial-use, derivative-model, trained-weight, APK-bundling, S3/CloudFront distribution, attribution, and NOTICE obligations;
9. documented public or approved authenticated access instructions, including whether URLs are signed/expiring and how checksums remain stable.

## Evidence supplied with the request

- Project repository revision: `4d77d8a402b680fb739c1a6a3ec531de0cf17139`.
- Pinned `data_links.txt` SHA-256: `d8c77ae455793da40597417b04f533d2c7109adab4ce478962ee542cd1ac1cb2`.
- Zenodo concept record `11636050`; public version record `14016558` contains only the 310,314-byte `v1.0.2` repository snapshot, not audio/manifests.
- Each documented target object URL returned HTTP 403 `AccessDenied` during ordinary unauthenticated retrieval. The repository/Zenodo materials do not document an authentication or registration route.

## Data-minimization request

A manifest/checksum/data-card package is sufficient for the next audit; bulk audio is not requested until those records pass review. Do not send credentials, secrets, or private signed URLs to the iTantra repository.
