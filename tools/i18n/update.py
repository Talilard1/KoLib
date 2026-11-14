"""
Finds the text the app translates now (every tr("...") and trc("...", "...") in the source), gives
new text the next numbers in en_list.json, and writes build/en_work.txt: every text by its number,
with the file it's in and, for text with a {0}, the line that fills it in. Text marked (unused) is
no longer in the app; its number is never given to other text.

  python3 update.py
"""
import json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'java', 'dev', 'kobolibrary', 'app')
BUILD = os.path.join(HERE, 'build')
os.makedirs(BUILD, exist_ok=True)
current_path = os.path.join(BUILD, 'current.json')
subprocess.run([sys.executable, os.path.join(HERE, 'collect.py'), SRC, current_path], check=True)

en_path = os.path.join(HERE, 'en_list.json')
EN = json.load(open(en_path, encoding='utf-8'))
cur = json.load(open(current_path, encoding='utf-8'))
added = [k for k in cur if k not in EN]
EN.extend(added)
json.dump(EN, open(en_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
for k in added:
    print('new', EN.index(k), repr(k))

cache = {}
def line(at):
    if ':' not in at:
        return ''
    f, n = at.rsplit(':', 1)
    if f not in cache:
        cache[f] = open(os.path.join(SRC, f), encoding='utf-8').read().split('\n')
    return cache[f][int(n) - 1].strip()

with open(os.path.join(BUILD, 'en_work.txt'), 'w', encoding='utf-8') as out:
    for i, k in enumerate(EN):
        at = cur.get(k)
        esc = json.dumps(k, ensure_ascii=False)[1:-1]
        if at is None:
            out.write(f"{i}|(unused)|{esc}\n")
            continue
        ctx = ''
        if re.search(r'\{\d\}', k):
            l = line(at)
            m = l.find('tr(')
            ctx = '   ⟨' + l[m:m + 150] + '⟩' if m >= 0 else ''
        out.write(f"{i}|{at.split(':')[0].split('/')[-1].replace('.kt', '')}|{esc}{ctx}\n")
print('wrote', os.path.join('build', 'en_work.txt'))
