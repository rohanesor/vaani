# Emergency-vocabulary gap report

## Result: UNKNOWN — corpus text was not available

The official IndicVoices-R repository describes normalized transcript fields, but no target-language manifests or transcripts were obtainable in the metadata-only phase. LibriTTS archive transcripts were not downloaded. Coverage cannot be inferred from a corpus title, published hours, or language membership.

The following must be counted per language before training:

| Category | Required examples / forms | Current coverage |
|---|---|---|
| Emergency alerts | send help, danger, injured, emergency, warning | UNKNOWN |
| Instructions | move, do not enter, call, wait, evacuate | UNKNOWN |
| Locations | hospital, shelter, station, building, entrance, street | UNKNOWN |
| Numbers | cardinal/ordinal numbers, counts, phone-like sequences | UNKNOWN |
| Dates/times | dates, clock times, durations | UNKNOWN |
| Names | representative Indian personal and place names | UNKNOWN |
| Mixed text | English abbreviations/loanwords and code-switching | UNKNOWN |
| Punctuation | pauses, questions, alerts, delimiters | UNKNOWN |

## Separate evaluation set

Create a native-speaker/linguist-reviewed, license-cleared evaluation set outside training data. It must contain short emergency, long emergency, location, numeric, name/location, and instruction sentences for every target language. Keep stable IDs, translator/reviewer provenance, canonical Unicode text, romanization only where needed for review, and no overlap with training text. Do not add these sentences to training based solely on poor coverage; any augmentation decision needs separate legal and evaluation review.

## Language-specific evaluation sentences

These are evaluation-only text targets, not training records or synthetic speech. Native speakers/linguists must review them before use.

| Language | Evaluation sentence |
|---|---|
| Hindi | आपातकाल है। कृपया मदद भेजें। हम घायल हैं और यहाँ फँसे हैं। हमारा स्थान और निर्देशांक भेज रहे हैं। |
| Gujarati | આ કટોકટી છે. કૃપા કરીને મદદ મોકલો. અમે ઘાયલ છીએ અને અહીં ફસાયેલા છીએ. અમારું સ્થાન અને નિર્દેશાંક મોકલી રહ્યા છીએ. |
| Marathi | ही आणीबाणी आहे. कृपया मदत पाठवा. आम्ही जखमी आहोत आणि येथे अडकलो आहोत. आमचे स्थान आणि निर्देशांक पाठवत आहोत. |
| Kannada | ಇದು ತುರ್ತು ಪರಿಸ್ಥಿತಿ. ದಯವಿಟ್ಟು ಸಹಾಯ ಕಳುಹಿಸಿ. ನಮಗೆ ಗಾಯವಾಗಿದೆ ಮತ್ತು ನಾವು ಇಲ್ಲಿ ಸಿಲುಕಿದ್ದೇವೆ. ನಮ್ಮ ಸ್ಥಳ ಮತ್ತು ನಿರ್ದೇಶಾಂಕಗಳನ್ನು ಕಳುಹಿಸುತ್ತಿದ್ದೇವೆ. |
| Malayalam | ഇത് അടിയന്തരാവസ്ഥയാണ്. ദയവായി സഹായം അയയ്ക്കുക. ഞങ്ങൾക്ക് പരിക്കേറ്റു, ഞങ്ങൾ ഇവിടെ കുടുങ്ങിയിരിക്കുകയാണ്. ഞങ്ങളുടെ സ്ഥലവും കോർഡിനേറ്റുകളും അയയ്ക്കുന്നു. |
| Tamil | இது அவசரநிலை. தயவுசெய்து உதவி அனுப்புங்கள். நாங்கள் காயமடைந்து இங்கே சிக்கியுள்ளோம். எங்கள் இருப்பிடத்தையும் ஆயத்தொலைவுகளையும் அனுப்புகிறோம். |
| Telugu | ఇది అత్యవసర పరిస్థితి. దయచేసి సహాయం పంపండి. మాకు గాయాలయ్యాయి, మేము ఇక్కడ చిక్కుకున్నాము. మా స్థానం మరియు కోఆర్డినేట్లను పంపుతున్నాము. |
| Odia | ଏହା ଏକ ଜରୁରୀ ପରିସ୍ଥିତି। ଦୟାକରି ସାହାଯ୍ୟ ପଠାନ୍ତୁ। ଆମେ ଆହତ ଏବଂ ଏଠାରେ ଫସିଛୁ। ଆମର ସ୍ଥାନ ଏବଂ ନିର୍ଦ୍ଦେଶାଙ୍କ ପଠାଉଛୁ। |
| Bengali | এটি জরুরি অবস্থা। দয়া করে সাহায্য পাঠান। আমরা আহত এবং এখানে আটকে আছি। আমাদের অবস্থান ও স্থানাঙ্ক পাঠাচ্ছি। |
| English | This is an emergency. Please send help. We are injured and trapped here. We are sending our location and coordinates. |

Include short isolated prompts for SOS, help, fire, danger, medical, water, food, safe, yes, no, wait, and come in the same reviewed evaluation set. Do not infer training coverage until the retained corpus is audited.

## Frontend implications

The local frontend must explicitly normalize numbers, dates, abbreviations, punctuation, Unicode NFC, and approved mixed-language tokens per routed packet language. Corpus audit results determine rules; Phase 2F creates no unvalidated normalization rewrite.
