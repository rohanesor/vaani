# Training-corpus license compatibility

| Dataset family | License evidence | Can enter one training pipeline? | Attribution / share obligations | Trained APK-weight decision |
|---|---|---|---|---|
| IndicVoices-R | CC-BY-4.0 declared | Potentially yes with CC-BY English data | preserve attribution, license link, change indication | Conditional; blocked on archive/data-card access and legal review |
| LibriTTS | CC-BY-4.0 | Potentially yes | same CC-BY obligations | Conditional; source manifest/checksum review required |
| OpenSLR Indic TTS sets | CC-BY-SA-4.0 | Technically yes, but license treatment must be resolved first | attribution and ShareAlike terms | **Blocked** until counsel determines trained-weight distribution/notice obligations |
| AI4Bharat Rasa | CC-BY-4.0 | Potentially yes | CC-BY attribution/change indication | Conditional; exact dataset artifact/provenance review required |
| Common Voice | Current release terms not verified in Phase 2H | Do not combine yet | Unknown | Blocked |
| AI4Bharat IndicVoices | License not identified in official repository | Do not combine | Unknown | Blocked |

License compatibility is not established merely by choosing a permissive project-code license. Audio, transcripts, speaker metadata, corpus manifests, trained teacher outputs, frontend text resources, and resulting weights need separate records. Where CC-BY-SA data is used, the legally conservative position is to block shipment until legal review determines whether and how ShareAlike obligations attach to trained weights and associated distribution.

## Required provenance record

Every training run must store immutable dataset release, archive checksum, selected record IDs, source license, attribution/NOTICE text, filtering-script revision, normalization version, split seed, training config, code/runtime revisions, teacher provenance if any, output model checksum, and distribution decision. No private speaker metadata belongs in Git.
