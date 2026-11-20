"""
Compiles every Regex("...") in the app's Kotlin with ICU, the regex engine Android's java.util.regex uses. The JVM's
engine (the one the unit tests run on) takes some patterns that ICU won't, such as a lone "}", so a pattern can pass
every test and still stop the app on a phone. Run before each build:

  python3 tools/regex/check.py

Needs ICU's library (libicui18n) on the machine; says which patterns don't compile, and exits 1 if any don't.
"""
import ctypes, ctypes.util, glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'java')
CONSTS = {'ChapterOpenings.SECTION_PART': 'kl-part-', 'SECTION_PART': 'kl-part-'}


def icu():
    for v in range(80, 60, -1):
        try:
            lib = ctypes.CDLL(f'libicui18n.so.{v}')
            return getattr(lib, f'uregex_open_{v}'), getattr(lib, f'uregex_close_{v}')
        except (OSError, AttributeError):
            continue
    sys.exit('ICU (libicui18n) not found.')


def unescape(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            out.append({'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\', '$': '$', "'": "'"}.get(s[i + 1], '\\' + s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return ''.join(out)


def literal_at(src, i):
    if src.startswith('"""', i):
        j = src.index('"""', i + 3)
        return src[i + 3:j], j + 3
    if src.startswith('"', i):
        j = i + 1
        while src[j] != '"':
            j += 2 if src[j] == '\\' else 1
        return unescape(src[i + 1:j]), j + 1
    for k, v in CONSTS.items():
        if src.startswith(k, i):
            return v, i + len(k)
    return None, i


def patterns():
    for path in sorted(glob.glob(os.path.join(SRC, '**', '*.kt'), recursive=True)):
        src = open(path, encoding='utf-8').read()
        for m in re.finditer(r'Regex\(\s*', src):
            i, parts = m.end(), []
            while True:
                val, j = literal_at(src, i)
                if val is None:
                    break
                parts.append(val)
                k = j
                while k < len(src) and src[k] in ' \t\n':
                    k += 1
                if not src.startswith('+', k):
                    break
                k += 1
                while k < len(src) and src[k] in ' \t\n':
                    k += 1
                i = k
            p = ''.join(parts)
            # Patterns built from values at run time can't be checked here.
            if parts and '${' not in p:
                yield os.path.relpath(path, SRC) + ':' + str(src[:m.start()].count('\n') + 1), p


def main():
    uopen, uclose = icu()
    uopen.restype = ctypes.c_void_p
    uopen.argtypes = [ctypes.c_char_p, ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p, ctypes.POINTER(ctypes.c_int32)]
    uclose.argtypes = [ctypes.c_void_p]
    count, bad = 0, 0
    for at, p in patterns():
        count += 1
        b = p.encode('utf-16-le')
        status = ctypes.c_int32(0)
        r = uopen(b, len(b) // 2, 0, None, ctypes.byref(status))
        if r:
            uclose(r)
        if status.value > 0:
            bad += 1
            print(f'Won\'t compile on Android ({status.value}): {at}\n    {p}')
    print(f'{count} patterns checked, {bad} that won\'t compile on Android.')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
