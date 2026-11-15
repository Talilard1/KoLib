"""
Checks the translations in tr/*.txt against the English list and writes the app's tables:
app/src/main/assets/i18n/<language>.json, from the English to each language's words.
Run update.py first, so it knows which text the app uses now.

  python3 build.py [--write]
"""
import glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
EN = json.load(open(os.path.join(HERE, 'en_list.json')))
LANGS = ['fr', 'de', 'es', 'it', 'nl', 'pt', 'ru', 'ja']
# How many forms a word has with a count, {0:...|...}, in languages that use them (see L10n.formFor).
FORMS = {'ru': 3}
OUT = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'i18n')
FILE_LANG = {'pt': 'pt-BR'}

def decode(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            out.append({'n': '\n', 't': '\t', '"': '"', '\\': '\\'}.get(s[i + 1], '\\' + s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return ''.join(out)

def french(s):
    # A no-break space before : ; ? ! and inside « », as French is set.
    s = re.sub(r' ([:;?!»])', ' \\1', s)
    s = s.replace('« ', '« ')
    return s

tables = {l: {} for l in LANGS}
problems = []
for path in sorted(glob.glob(os.path.join(HERE, 'tr', '*.txt'))):
    lang = None
    for n, line in enumerate(open(path, encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line.strip():
            continue
        if line.startswith('@'):
            lang = line[1:].strip()
            assert lang in LANGS, (path, n, lang)
            continue
        m = re.match(r'(\d+)\|(.*)$', line)
        if not m:
            problems.append(f'{os.path.basename(path)}:{n}: not "id|text": {line[:60]}')
            continue
        i, text = int(m.group(1)), decode(m.group(2))
        if text == '=' and i < len(EN):
            # The same as in English (a name, a licence).
            text = EN[i]
        if i >= len(EN):
            problems.append(f'{os.path.basename(path)}:{n}: no English text {i}')
            continue
        en = EN[i]
        if i in tables[lang]:
            problems.append(f'{os.path.basename(path)}:{n}: {lang} {i} given twice')
        if sorted(re.findall(r'\{\d+\}', en)) != sorted(re.findall(r'\{\d+\}', text)):
            problems.append(f'{os.path.basename(path)}:{n}: {lang} {i} placeholders differ: {en!r} -> {text!r}')
        for k, forms in re.findall(r'\{(\d+):([^{}]*)\}', text):
            if '{%s}' % k not in en:
                problems.append(f'{os.path.basename(path)}:{n}: {lang} {i} counts {{{k}}}, which the English hasn\'t got')
            if len(forms.split('|')) != FORMS.get(lang, 2):
                problems.append(f'{os.path.basename(path)}:{n}: {lang} {i} {{{k}:...}} needs {FORMS.get(lang, 2)} forms: {forms!r}')
        # Spaces at the ends join the text to what comes before or after it: kept as in the English.
        text = text.strip(' ')
        if en.startswith(' '):
            text = ' ' + text
        if en.endswith(' '):
            text = text + ' '
        if en.count('\n') != text.count('\n'):
            problems.append(f'{os.path.basename(path)}:{n}: {lang} {i} line breaks differ ({en.count(chr(10))} vs {text.count(chr(10))})')
        tables[lang][i] = french(text) if lang == 'fr' else text

for p in problems:
    print('PROBLEM', p)
CURRENT = json.load(open(os.path.join(HERE, 'build', 'current.json')))
for l in LANGS:
    missing = [i for i in range(len(EN)) if i not in tables[l] and EN[i] in CURRENT]
    print(f'{l}: {len(EN) - len(missing)}/{len(EN)}' + (f' (missing: {len(missing)}, first {missing[0]})' if missing else ''))

if '--write' in sys.argv:
    os.makedirs(OUT, exist_ok=True)
    for l in LANGS:
        table = {EN[i]: t for i, t in sorted(tables[l].items()) if t != EN[i] and EN[i] in CURRENT}
        name = FILE_LANG.get(l, l)
        with open(os.path.join(OUT, f'{name}.json'), 'w', encoding='utf-8') as f:
            json.dump(table, f, ensure_ascii=False, indent=0, sort_keys=True)
        print('wrote', name, len(table))
