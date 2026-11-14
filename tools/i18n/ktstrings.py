"""
Finds the string literals in Kotlin source, as the compiler sees them: ordinary strings with their
templates ($name and ${expression}, which may hold strings of their own), raw strings, comments
and character literals.

Each literal found is a dict: start and end (offsets of the opening and closing quote, end exclusive),
raw (True for triple-quoted), parts (a list of ('text', s) and ('expr', code) in order), depth (how
many templates it's inside of: 0 for a literal in plain code).
"""


def scan(src):
    out = []
    i = 0
    n = len(src)

    def skip_comment(i):
        if src.startswith('//', i):
            j = src.find('\n', i)
            return n if j < 0 else j
        if src.startswith('/*', i):
            depth = 0
            while i < n:
                if src.startswith('/*', i):
                    depth += 1
                    i += 2
                elif src.startswith('*/', i):
                    depth -= 1
                    i += 2
                    if depth == 0:
                        return i
                else:
                    i += 1
            return n
        return i

    def read_string(i, depth):
        """i at the opening quote. Returns the index after the closing quote."""
        raw = src.startswith('"""', i)
        start = i
        i += 3 if raw else 1
        parts = []
        text = []
        while i < n:
            c = src[i]
            if raw and src.startswith('"""', i):
                # A raw string ends at the last of a run of quotes.
                j = i
                while j < n and src[j] == '"':
                    j += 1
                text.append('"' * (j - i - 3))
                i = j
                break
            if not raw and c == '"':
                i += 1
                break
            if not raw and c == '\\':
                text.append(src[i:i + 2])
                i += 2
                continue
            if c == '$' and i + 1 < n and src[i + 1] == '{':
                if text:
                    parts.append(('text', ''.join(text)))
                    text = []
                j = i + 2
                expr_start = j
                level = 1
                while j < n and level > 0:
                    if src[j] == '"':
                        j = read_string(j, depth + 1)
                        continue
                    if src.startswith('//', j) or src.startswith('/*', j):
                        j = skip_comment(j)
                        continue
                    if src[j] == "'":
                        j = skip_char(j)
                        continue
                    if src[j] == '{':
                        level += 1
                    elif src[j] == '}':
                        level -= 1
                        if level == 0:
                            break
                    j += 1
                parts.append(('expr', src[expr_start:j]))
                i = j + 1
                continue
            if c == '$' and i + 1 < n and (src[i + 1].isalpha() or src[i + 1] == '_'):
                if text:
                    parts.append(('text', ''.join(text)))
                    text = []
                j = i + 1
                while j < n and (src[j].isalnum() or src[j] == '_'):
                    j += 1
                parts.append(('expr', src[i + 1:j]))
                i = j
                continue
            text.append(c)
            i += 1
        if text:
            parts.append(('text', ''.join(text)))
        out.append({'start': start, 'end': i, 'raw': raw, 'parts': parts, 'depth': depth})
        return i

    def skip_char(i):
        # 'x', '\n', ' '
        j = i + 1
        if j < n and src[j] == '\\':
            j += 2
            while j < n and src[j] != "'":
                j += 1
        else:
            j += 1
        return j + 1 if j < n and src[j] == "'" else i + 1

    while i < n:
        c = src[i]
        if src.startswith('//', i) or src.startswith('/*', i):
            i = skip_comment(i)
        elif c == '"':
            i = read_string(i, 0)
        elif c == "'":
            i = skip_char(i)
        else:
            i += 1
    out.sort(key=lambda s: s['start'])
    return out


def text_of(lit):
    """The literal with its templates as {0}, {1}... and the expressions in order."""
    t = []
    args = []
    for kind, v in lit['parts']:
        if kind == 'text':
            t.append(v)
        else:
            t.append('{%d}' % len(args))
            args.append(v)
    return ''.join(t), args


if __name__ == '__main__':
    import sys
    src = open(sys.argv[1], encoding='utf-8').read()
    for lit in scan(src):
        t, a = text_of(lit)
        line = src.count('\n', 0, lit['start']) + 1
        print(line, lit['depth'], repr(t), a)
