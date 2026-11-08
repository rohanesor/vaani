#!/usr/bin/env python3
"""Report Unicode/text features from a private JSONL manifest; never rewrites it."""
import argparse
import json
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

INDIC_SCRIPTS = {
    'Devanagari': ('\u0900', '\u097f'), 'Gujarati': ('\u0a80', '\u0aff'),
    'Kannada': ('\u0c80', '\u0cff'), 'Malayalam': ('\u0d00', '\u0d7f'),
    'Tamil': ('\u0b80', '\u0bff'), 'Telugu': ('\u0c00', '\u0c7f'),
    'Odia': ('\u0b00', '\u0b7f'), 'Bengali': ('\u0980', '\u09ff'),
}
def script_of(ch):
    return next((name for name, (a, b) in INDIC_SCRIPTS.items() if a <= ch <= b), 'Other')
def main():
    p=argparse.ArgumentParser(); p.add_argument('manifest',type=Path); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
    out=defaultdict(lambda: {'records':0,'notNfc':0,'digits':0,'latin':0,'punctuation':0,'scripts':Counter(),'symbols':Counter()})
    for line in a.manifest.read_text(encoding='utf-8').splitlines():
        if not line.strip(): continue
        row=json.loads(line); lang=str(row.get('language','UNKNOWN')).lower(); text=str(row.get('transcript',''))
        item=out[lang]; item['records']+=1; item['notNfc'] += text != unicodedata.normalize('NFC',text)
        item['digits'] += bool(re.search(r'\d',text)); item['latin'] += bool(re.search(r'[A-Za-z]',text)); item['punctuation'] += bool(re.search(r'[^\w\s]',text))
        for ch in text:
            item['scripts'][script_of(ch)] += 1
            if unicodedata.category(ch).startswith('S'): item['symbols'][ch] += 1
    result={'schemaVersion':1,'languages':{}}
    for lang,item in sorted(out.items()):
        item['scripts']=dict(item['scripts']); item['symbols']=dict(item['symbols'].most_common(100)); result['languages'][lang]=item
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
if __name__=='__main__': main()
