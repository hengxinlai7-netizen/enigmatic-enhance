import io
import os
import re

SRC = r'C:\Users\hengx\Documents\deepseek-harness\default-workspace\enigmatic-enhance\src\main\java\com\hengx\enigmaticenhance\RingConfig.java'
OUT = r'C:\Users\hengx\Downloads\enigmaticenhance-common.toml'

lines = io.open(SRC, encoding='utf-8').read().splitlines()

stack = []
entries = []          # (path, key, value_literal, comment, range)
pending_comments = []

# 合并成逻辑语句：以 ';' 结尾的算一条语句
buf = ''
for raw in lines:
    line = raw.strip()
    if not line:
        continue
    buf += (' ' if buf else '') + line
    if not line.endswith(';'):
        continue
    stmt = re.sub(r'\s+', ' ', buf).strip()
    buf = ''

    # 收集注释
    for cm in re.finditer(r'\.comment\("((?:[^"\\]|\\.)*)"\)', stmt):
        pending_comments.append(cm.group(1))

    # push / pop
    for pm in re.finditer(r'\.push\("([^"]+)"\)', stmt):
        stack.append(pm.group(1))

    # define / defineInRange
    m = re.search(r'\.defineInRange\("([^"]+)",\s*([^,]+),\s*([^,]+),\s*([^)]+)\)', stmt)
    if m:
        key, val, lo, hi = m.group(1), m.group(2).strip(), m.group(3).strip(), m.group(4).strip()
        entries.append((list(stack), key, val, list(pending_comments), (lo, hi)))
        pending_comments = []
    else:
        m2 = re.search(r'\.define\("([^"]+)",\s*(.+?)\)\s*;', stmt)
        if m2:
            entries.append((list(stack), m2.group(1), m2.group(2).strip(), list(pending_comments), None))
            pending_comments = []

    pops = stmt.count('.pop()')
    for _ in range(pops):
        if stack:
            stack.pop()

print('解析到配置项:', len(entries))

def fmt_value(v):
    s = v.strip()
    if re.fullmatch(r'-?\d+', s):
        return s
    if re.fullmatch(r'-?\d+\.\d*[dDfF]?', s):
        return s.rstrip('dDfF')
    if s.lower() in ('true', 'false'):
        return s.lower()
    if s.startswith('"') and s.endswith('"'):
        return s
    if re.fullmatch(r'[A-Za-z0-9_.:\-]+', s) and '::' in s:
        return s
    return s

out = []
out.append('# ============================================================================')
out.append('# 神秘遗物增强 (Enigmatic Enhance) 完整配置  ——  对应 mod 版本 2.1.0')
out.append('# 放到 .minecraft/config/enigmaticenhance-common.toml 覆盖旧文件即可。')
out.append('# 所有键都写全了，不会再出现 "Incorrect key ... was corrected from null" 的补全警告。')
out.append('# ============================================================================')
out.append('')

current = None
for path, key, val, comments, rng in entries:
    sec = '.'.join(path) if path else ''
    if sec != current:
        out.append('')
        out.append('[%s]' % sec)
        current = sec
    for c in comments:
        out.append('# ' + c)
    if rng:
        out.append('# 取值区间: %s ~ %s' % (rng[0], rng[1]))
    out.append('\t%s = %s' % (key, fmt_value(val)))

text = '\n'.join(out).replace('\r\n', '\n') + '\n'
io.open(OUT, 'w', encoding='utf-8', newline='\n').write(text)
print('已写出:', OUT)
print('行数:', len(out))
