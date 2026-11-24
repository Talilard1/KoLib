import math
from shapely.geometry import Polygon, Point, LineString, box
from shapely.ops import unary_union
from shapely import affinity

def bez(p0, p1, p2, p3, n=24):
    out = []
    for i in range(1, n + 1):
        t = i / n; u = 1 - t
        out.append((u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
                    u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]))
    return out

def quad(p0, p1, p2, n=16):
    out = []
    for i in range(1, n + 1):
        t = i / n; u = 1 - t
        out.append((u*u*p0[0] + 2*u*t*p1[0] + t*t*p2[0], u*u*p0[1] + 2*u*t*p1[1] + t*t*p2[1]))
    return out

cat = [(5.2, 9.6), (4.9, 3.9), (9.4, 6.7)] + quad((9.4, 6.7), (12, 5.9), (14.6, 6.7)) + [(19.1, 3.9), (18.8, 9.6)]
cat += bez((18.8, 9.6), (19.6, 10.9), (20, 12.3), (20, 13.8)) + bez((20, 13.8), (20, 18), (16.4, 20.6), (12, 20.6))
cat += bez((12, 20.6), (7.6, 20.6), (4, 18), (4, 13.8)) + bez((4, 13.8), (4, 12.3), (4.4, 10.9), (5.2, 9.6))
cat = Polygon(cat)

bc = [(12, 6.3)] + bez((12, 6.3), (9, 6.3), (6.4, 7.6), (5.2, 9.6)) + bez((5.2, 9.6), (4.4, 10.9), (4, 12.3), (4, 13.8))
bc += bez((4, 13.8), (4, 18), (7.6, 20.6), (12, 20.6)) + bez((12, 20.6), (16.4, 20.6), (20, 18), (20, 13.8))
bc += bez((20, 13.8), (20, 12.3), (19.6, 10.9), (18.8, 9.6)) + [(19.1, 3.9), (14.6, 6.7)] + quad((14.6, 6.7), (13.3, 6.3), (12, 6.3))
bc = Polygon(bc)

W = 0.95
legs = [LineString([(5.6, 10.7), (2.5, 8.9)]), LineString([(5.2, 13.8), (1.7, 13.8)]), LineString([(5.6, 16.9), (2.6, 18.7)])]
feeler = LineString([(9.6, 7.4), (7.6, 3.6)])
hole = unary_union([bc] + [l.buffer(W, cap_style='round') for l in legs] + [feeler.buffer(W, cap_style='round'), Point(7.4, 3.2).buffer(1.35)])
solid_back = unary_union([Point(8.8, 12.5).buffer(1.35), Point(8.5, 16.8).buffer(1.05), box(11.35, 4.8, 12.65, 21.6)])

S = 0.78
disc = Point(12, 12).buffer(11, resolution=64)

def ring_d(coords):
    pts = list(coords)[:-1]
    return 'M' + 'L'.join(f'{x:.2f} {y:.2f}' for x, y in pts) + 'Z'

def path_of(geom):
    polys = [geom] if geom.geom_type == 'Polygon' else list(geom.geoms)
    out = []
    for p in polys:
        out.append(ring_d(p.exterior.coords))
        out += [ring_d(r.coords) for r in p.interiors]
    return ' '.join(out)

release = disc.difference(affinity.scale(cat, S, S, origin=(12, 12))).simplify(0.02)
report = disc.difference(affinity.scale(hole, S, S, origin=(12, 12))).union(affinity.scale(solid_back, S, S, origin=(12, 12)).intersection(disc)).simplify(0.02)
rd, pd = path_of(release), path_of(report)
for name, d in [('rel_s', rd), ('rep_s', pd)]:
    open(name + '.svg', 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path fill="black" fill-rule="evenodd" d="{d}"/></svg>')
open('shapely_paths.txt', 'w').write(rd + '\n' + pd)
print(len(rd), len(pd), report.geom_type)
