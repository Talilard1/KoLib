"""Lists the text KoLib translates: every tr("...") in the source, and the phrases Fmt.plural makes."""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from ktstrings import scan


def kunescape(s):
    """A Kotlin literal's text (as written in the source) as it is when the app runs."""
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '\\' and i + 1 < len(s):
            d = s[i + 1]
            if d == 'u':
                out.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({'n': '\n', 't': '\t', 'r': '\r', 'b': '\b', '"': '"', "'": "'", '\\': '\\', '$': '$'}.get(d, d))
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)



ROOT = sys.argv[1]
OUT = sys.argv[2]
keys = {}
plural_nouns = set()
for dirpath, _, files in os.walk(ROOT):
    for f in sorted(files):
        if not f.endswith('.kt'):
            continue
        path = os.path.join(dirpath, f)
        rel = os.path.relpath(path, ROOT).replace(os.sep, '/')
        src = open(path, encoding='utf-8').read()
        for lit in scan(src):
            before = src[max(0, lit['start'] - 30):lit['start']]
            line_no = src.count('\n', 0, lit['start']) + 1
            if re.search(r'\btr\(\s*$', before):
                if any(k != 'text' for k, _ in lit['parts']):
                    print('template inside tr():', rel, line_no)
                text = kunescape(''.join(v for _, v in lit['parts']))
                keys.setdefault(text, rel + ':' + str(line_no))
        for m in re.finditer(r'\btrc\("([^"]+)",\s*"([^"]+)"\)', src):
            keys.setdefault(m.group(1) + '|' + m.group(2), rel)
        for m in re.finditer(r'Fmt\.plural\((?:[^()]|\([^()]*\))*?,\s*"([^"]+)"(?:,\s*"([^"]+)")?\)', src):
            one, many = m.group(1), m.group(2) or m.group(1) + 's'
            plural_nouns.add((one, many))
for one, many in sorted(plural_nouns):
    keys.setdefault('{0} ' + one, 'Fmt.plural')
    keys.setdefault('{0} ' + many, 'Fmt.plural')
json.dump(keys, open(OUT, 'w'), indent=1, ensure_ascii=False)
print(len(keys), 'keys;', 'plural nouns:', sorted(plural_nouns))
