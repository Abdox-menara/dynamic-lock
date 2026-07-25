#!/usr/bin/env python3
"""Reference implementation of PinEngine.kt, asserted against DroidLock's documented
examples. Mirrors the Kotlin logic exactly (same offset math, same add-on order)."""

ADDON_ORDER = ["DOUBLE", "MIRROR", "SUM", "REVERSE"]

def pad2(n):
    v = 0 if n < 0 else n
    return f"0{v}" if v < 10 else str(v)

def render(c, inp, h24, m, h12):
    y, mo, d, batt = inp["year"], inp["month"], inp["day"], inp["battery"]
    if c == "TIME_12": return pad2(h12) + pad2(m)
    if c == "TIME_24": return pad2(h24) + pad2(m)
    if c == "HOUR_12": return pad2(h12)
    if c == "HOUR_24": return pad2(h24)
    if c == "MINUTE":  return pad2(m)
    if c == "DAY":     return pad2(d)
    if c == "MONTH":   return pad2(mo)
    if c == "YEAR_2":  return pad2(((y % 100) + 100) % 100)
    if c == "YEAR_4":  return str(y).zfill(4)
    if c == "BATTERY": return pad2(batt) if 0 <= batt <= 99 else str(batt)
    raise ValueError(c)

def apply_addon(a, s):
    if a == "DOUBLE":  return s + s
    if a == "MIRROR":  return s + s[::-1]
    if a == "REVERSE": return s[::-1]
    if a == "SUM":
        total = sum(int(ch) for ch in s if ch.isdigit())
        ds = str(total)
        out = ""
        while len(out) < 4:
            out += ds
        return out[:4]
    raise ValueError(a)

def compute(comps, offset, adds, inp):
    eff = (((inp["hour24"] * 60 + inp["minute"] + offset) % 1440) + 1440) % 1440
    h24, m = eff // 60, eff % 60
    h12 = ((h24 + 11) % 12) + 1
    pin = "".join(render(c, inp, h24, m, h12) for c in comps)
    for a in ADDON_ORDER:
        if a in adds:
            pin = apply_addon(a, pin)
    return pin

def inp(y, mo, d, h, mi, b):
    return {"year": y, "month": mo, "day": d, "hour24": h, "minute": mi, "battery": b}

t0123 = inp(2016, 5, 4, 1, 23, 52)    # May 4 2016, 01:23, battery 52%
t1323 = inp(2016, 5, 4, 13, 23, 52)   # 13:23
t1234 = inp(2016, 5, 4, 12, 34, 52)   # 12:34

cases = [
    ("Time 12h (01:23)",        (["TIME_12"], 0, []),               t0123, "0123"),
    ("Time 12h +10min (01:23)", (["TIME_12"], 10, []),              t0123, "0133"),
    ("Time 24h (13:23)",        (["TIME_24"], 0, []),               t1323, "1323"),
    ("Date intl DD/MM",         (["DAY","MONTH"], 0, []),           t0123, "0405"),
    ("Date USA MM/DD",          (["MONTH","DAY"], 0, []),           t0123, "0504"),
    ("Date intl DD/MM/YY",      (["DAY","MONTH","YEAR_2"], 0, []),  t0123, "040516"),
    ("Date intl DD/MM/YYYY",    (["DAY","MONTH","YEAR_4"], 0, []),  t0123, "04052016"),
    ("Geek bat+h12+mon+min",    (["BATTERY","HOUR_12","MONTH","MINUTE"], 0, []), t0123, "52010523"),
    ("Battery 52% (double)",    (["BATTERY"], 0, ["DOUBLE"]),       t0123, "5252"),
    ("Add-on Double (1234)",    (["TIME_24"], 0, ["DOUBLE"]),       t1234, "12341234"),
    ("Add-on Mirror (1234)",    (["TIME_24"], 0, ["MIRROR"]),       t1234, "12344321"),
    ("Add-on Sum (1234)",       (["TIME_24"], 0, ["SUM"]),          t1234, "1010"),
    ("Add-on Reverse (1234)",   (["TIME_24"], 0, ["REVERSE"]),      t1234, "4321"),
]

print("Verifying dynamic-PIN engine against DroidLock's documented examples:\n")
passed = failed = 0
for name, (comps, off, adds), data, want in cases:
    got = compute(comps, off, adds, data)
    ok = got == want
    passed += ok
    failed += (not ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name:<26} got={got:<10} want={want}")

print(f"\nRESULT  passed={passed}  failed={failed}")
raise SystemExit(1 if failed else 0)
