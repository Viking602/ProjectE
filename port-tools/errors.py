from pathlib import Path
import re, json, sys, collections
log = Path(sys.argv[1]).read_text(encoding='utf-8-sig').split('FAILURE:')[0]
errors = re.findall(r'([^\n]+\.java):(\d+): error: ([^\n]+)\n(.*?)(?=\n[^\n]+\.java:\d+:|\nNote:|\n\d[\d,]* errors|$)', log, re.S)
Path('errors.json').write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding='utf-8')
if len(sys.argv) > 2:
    for f, line, message, context in errors:
        if re.search(sys.argv[2], f):
            print(f'{Path(f).name}:{line}: {message}\n{context}')
else:
    print('Total', len(errors))
    print(collections.Counter(Path(f).name for f, _, _, _ in errors).most_common())
