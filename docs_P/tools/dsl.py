"""
Tiny diagram DSL: every diagram is drawn once and exported both as a
draw.io page (mxGraph XML) and as an SVG (rendered to PNG with resvg).
"""
import math
from xml.sax.saxutils import escape

FONT = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, Courier New, monospace"

# fill, stroke, font colour per kind (draw.io's standard palette)
KINDS = {
    "mds":    ("#dae8fc", "#6c8ebf", "#000000"),
    "bank":   ("#d5e8d4", "#82b366", "#000000"),
    "data":   ("#fff2cc", "#d6b656", "#000000"),
    "thread": ("#e1d5e7", "#9673a6", "#000000"),
    "fail":   ("#f8cecc", "#b85450", "#000000"),
    "io":     ("#f5f5f5", "#666666", "#000000"),
    "note":   ("#ffe6cc", "#d79b00", "#000000"),
    "white":  ("#ffffff", "#6c8ebf", "#000000"),
    "whiteg": ("#ffffff", "#82b366", "#000000"),
    "whitey": ("#ffffff", "#d6b656", "#000000"),
    "plain":  ("#ffffff", "#999999", "#000000"),
    "title":  ("none", "none", "#000000"),
}

C_UP = "#2e7d32"     # bank -> server
C_DOWN = "#1f5fa8"   # server -> bank
C_GREY = "#666666"
C_RED = "#b85450"
C_ORANGE = "#d79b00"
C_PURPLE = "#7b4f9d"


def parse_line(line):
    """Prefixes: ** bold, ~~ small grey, ` monospace (combinable)."""
    bold = small = mono = False
    changed = True
    while changed:
        changed = False
        if line.startswith("**"):
            bold, line, changed = True, line[2:], True
        if line.startswith("~~"):
            small, line, changed = True, line[2:], True
        if line.startswith("`"):
            mono, line, changed = True, line[1:], True
    return line, bold, small, mono


class Diagram:
    def __init__(self, key, name, w, h):
        self.key, self.name, self.w, self.h = key, name, w, h
        self.cells = []

    # ---------------------------------------------------------------- shapes
    def box(self, x, y, w, h, text="", kind="plain", fs=14, align="center",
            valign="middle", dashed=False, rounded=True, shape="rect",
            sw=1.5, fill=None, stroke=None, lh=1.32):
        f, s, fc = KINDS[kind]
        self.cells.append(dict(t="box", x=x, y=y, w=w, h=h, text=text,
                               fill=fill or f, stroke=stroke or s, fc=fc,
                               fs=fs, align=align, valign=valign,
                               dashed=dashed, rounded=rounded, shape=shape,
                               sw=sw, lh=lh))

    def container(self, x, y, w, h, title, kind, fs=16, dashed=False):
        """Big rounded group with its title at the top."""
        self.box(x, y, w, h, title, kind, fs=fs, valign="top",
                 dashed=dashed, sw=2)

    def text(self, x, y, w, h, text, fs=14, align="left", valign="top",
             color="#000000", lh=1.32):
        self.cells.append(dict(t="box", x=x, y=y, w=w, h=h, text=text,
                               fill="none", stroke="none", fc=color, fs=fs,
                               align=align, valign=valign, dashed=False,
                               rounded=False, shape="rect", sw=0, lh=lh))

    def title(self, text, sub=None):
        self.text(30, 18, self.w - 60, 34, "**" + text, fs=24)
        if sub:
            self.text(30, 54, self.w - 60, 24, "~~" + sub, fs=15,
                      color="#444444")

    def edge(self, pts, label=None, color="#333333", dashed=False,
             start=False, end=True, sw=2, pos=0.5, fs=12, badge=False,
             under=False):
        self.cells.append(dict(t="edge", pts=pts, label=label, color=color,
                               dashed=dashed, start=start, end=end, sw=sw,
                               pos=pos, fs=fs, badge=badge, under=under))

    # ------------------------------------------------------------ draw.io
    @staticmethod
    def _html(text, fs):
        parts = []
        for raw in text.split("\n"):
            line, bold, small, mono = parse_line(raw)
            h = escape(line) if line else "&nbsp;"
            if mono:
                h = '<font face="Courier New">%s</font>' % h
            if small:
                h = '<font style="font-size:%dpx" color="#444444">%s</font>' % (fs - 2, h)
            if bold:
                h = "<b>%s</b>" % h
            parts.append(h)
        return "<br>".join(parts)

    def drawio(self, page_id):
        out = ['<diagram id="%s" name="%s">' % (page_id, escape(self.name)),
               '<mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" '
               'guides="1" tooltips="1" connect="1" arrows="1" fold="1" '
               'page="1" pageScale="1" pageWidth="%d" pageHeight="%d" '
               'background="#ffffff" math="0" shadow="0"><root>'
               % (self.w, self.h),
               '<mxCell id="0"/><mxCell id="1" parent="0"/>']
        for i, c in enumerate(self.cells):
            cid = "%s-%d" % (page_id, i)
            if c["t"] == "box":
                if c["shape"] == "rhombus":
                    st = "rhombus;"
                elif c["shape"] == "ellipse":
                    st = "ellipse;"
                elif c["fill"] == "none":
                    st = "text;"
                else:
                    st = "rounded=%d;arcSize=%d;" % (1 if c["rounded"] else 0, 8)
                st += ("whiteSpace=wrap;html=1;fillColor=%s;strokeColor=%s;"
                       "fontColor=%s;fontSize=%d;fontFamily=Helvetica;"
                       "align=%s;verticalAlign=%s;strokeWidth=%s;"
                       "spacingLeft=6;spacingRight=6;spacingTop=4;"
                       % (c["fill"], c["stroke"], c["fc"], c["fs"],
                          c["align"], c["valign"], c["sw"]))
                if c["dashed"]:
                    st += "dashed=1;"
                out.append('<mxCell id="%s" value="%s" style="%s" vertex="1" '
                           'parent="1"><mxGeometry x="%d" y="%d" width="%d" '
                           'height="%d" as="geometry"/></mxCell>'
                           % (cid, escape(self._html(c["text"], c["fs"]),
                                          {'"': "&quot;"}),
                              st, c["x"], c["y"], c["w"], c["h"]))
            else:
                p = c["pts"]
                st = ("html=1;rounded=0;strokeColor=%s;strokeWidth=%s;"
                      "endArrow=%s;endFill=1;startArrow=%s;startFill=1;"
                      "fontSize=%d;labelBackgroundColor=#ffffff;fontStyle=1;"
                      % (c["color"], c["sw"],
                         "block" if c["end"] else "none",
                         "block" if c["start"] else "none", c["fs"]))
                if c["dashed"]:
                    st += "dashed=1;"
                if c["badge"]:
                    st += "labelBorderColor=%s;" % c["color"]
                pts = "".join('<mxPoint x="%d" y="%d"/>' % q for q in p[1:-1])
                lab = escape(self._html(c["label"], c["fs"]),
                             {'"': "&quot;"}) if c["label"] else ""
                out.append('<mxCell id="%s" value="%s" style="%s" edge="1" '
                           'parent="1"><mxGeometry x="%.2f" relative="1" '
                           'as="geometry"><mxPoint x="%d" y="%d" '
                           'as="sourcePoint"/><mxPoint x="%d" y="%d" '
                           'as="targetPoint"/>%s</mxGeometry></mxCell>'
                           % (cid, lab, st, c["pos"] * 2 - 1, p[0][0], p[0][1],
                              p[-1][0], p[-1][1],
                              '<Array as="points">%s</Array>' % pts if pts else ""))
        out.append("</root></mxGraphModel></diagram>")
        return "".join(out)

    # ---------------------------------------------------------------- SVG
    @staticmethod
    def _tw(s, fs, bold, mono):
        k = 0.61 if mono else (0.57 if bold else 0.53)
        return len(s) * fs * k

    def _svg_text(self, c):
        lines = [parse_line(l) for l in c["text"].split("\n")] if c["text"] else []
        if not lines:
            return ""
        sizes = [(c["fs"] - 2 if sm else c["fs"]) for (_, _, sm, _) in lines]
        heights = [s * c["lh"] for s in sizes]
        total = sum(heights)
        pad = 8
        if c["valign"] == "top":
            y = c["y"] + pad + 2
        elif c["valign"] == "bottom":
            y = c["y"] + c["h"] - pad - total
        else:
            y = c["y"] + (c["h"] - total) / 2
        if c["align"] == "left":
            x, anchor = c["x"] + pad + 2, "start"
        elif c["align"] == "right":
            x, anchor = c["x"] + c["w"] - pad - 2, "end"
        else:
            x, anchor = c["x"] + c["w"] / 2, "middle"
        out = []
        for (line, bold, small, mono), s, hh in zip(lines, sizes, heights):
            base = y + hh * 0.5 + s * 0.35
            color = "#444444" if small else c["fc"]
            out.append('<text x="%.1f" y="%.1f" font-family="%s" '
                       'font-size="%d" font-weight="%s" fill="%s" '
                       'text-anchor="%s">%s</text>'
                       % (x, base, MONO if mono else FONT, s,
                          "bold" if bold else "normal", color, anchor,
                          escape(line)))
            y += hh
        return "".join(out)

    @staticmethod
    def _arrowhead(p, q, color, size=11):
        ang = math.atan2(q[1] - p[1], q[0] - p[0])
        a1, a2 = ang + math.radians(155), ang - math.radians(155)
        pts = [q, (q[0] + size * math.cos(a1), q[1] + size * math.sin(a1)),
               (q[0] + size * math.cos(a2), q[1] + size * math.sin(a2))]
        return '<polygon points="%s" fill="%s"/>' % (
            " ".join("%.1f,%.1f" % t for t in pts), color)

    @staticmethod
    def _point_at(pts, frac):
        segs = [(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
        lens = [math.dist(a, b) for a, b in segs]
        target = sum(lens) * frac
        for (a, b), L in zip(segs, lens):
            if target <= L and L > 0:
                t = target / L
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            target -= L
        return pts[-1]

    def svg(self):
        o = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
             'viewBox="0 0 %d %d"><rect width="100%%" height="100%%" '
             'fill="#ffffff"/>' % (self.w, self.h, self.w, self.h)]
        # "under" edges first, then boxes, then edges, then edge labels
        for c in self.cells:
            if c["t"] == "edge" and c["under"]:
                o.append('<polyline points="%s" fill="none" stroke="%s" '
                         'stroke-width="%s"%s/>' % (
                             " ".join("%d,%d" % tuple(t) for t in c["pts"]),
                             c["color"], c["sw"],
                             ' stroke-dasharray="8,6"' if c["dashed"] else ""))
        for c in self.cells:
            if c["t"] != "box":
                continue
            dash = ' stroke-dasharray="7,5"' if c["dashed"] else ""
            if c["fill"] != "none":
                if c["shape"] == "rhombus":
                    cx, cy = c["x"] + c["w"] / 2, c["y"] + c["h"] / 2
                    o.append('<polygon points="%s" fill="%s" stroke="%s" '
                             'stroke-width="%s"%s/>' % (
                                 "%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f" % (
                                     cx, c["y"], c["x"] + c["w"], cy, cx,
                                     c["y"] + c["h"], c["x"], cy),
                                 c["fill"], c["stroke"], c["sw"], dash))
                elif c["shape"] == "ellipse":
                    o.append('<ellipse cx="%.1f" cy="%.1f" rx="%.1f" ry="%.1f" '
                             'fill="%s" stroke="%s" stroke-width="%s"%s/>' % (
                                 c["x"] + c["w"] / 2, c["y"] + c["h"] / 2,
                                 c["w"] / 2, c["h"] / 2, c["fill"],
                                 c["stroke"], c["sw"], dash))
                else:
                    r = min(12, c["h"] * 0.08) if c["rounded"] else 0
                    if c["rounded"]:
                        r = max(r, 6)
                    o.append('<rect x="%d" y="%d" width="%d" height="%d" '
                             'rx="%.1f" fill="%s" stroke="%s" '
                             'stroke-width="%s"%s/>' % (
                                 c["x"], c["y"], c["w"], c["h"], r,
                                 c["fill"], c["stroke"], c["sw"], dash))
            o.append(self._svg_text(c))
        labels = []
        for c in self.cells:
            if c["t"] != "edge" or c["under"]:
                continue
            p = c["pts"]
            dash = ' stroke-dasharray="8,6"' if c["dashed"] else ""
            # shorten line ends so they do not poke through the arrowheads
            q = [list(t) for t in p]
            o.append('<polyline points="%s" fill="none" stroke="%s" '
                     'stroke-width="%s"%s stroke-linejoin="round"/>' % (
                         " ".join("%d,%d" % tuple(t) for t in q), c["color"],
                         c["sw"], dash))
            if c["end"]:
                o.append(self._arrowhead(p[-2], p[-1], c["color"]))
            if c["start"]:
                o.append(self._arrowhead(p[1], p[0], c["color"]))
            if c["label"]:
                labels.append(c)
        for c in labels:
            mx, my = self._point_at(c["pts"], c["pos"])
            lines = [parse_line(l) for l in c["label"].split("\n")]
            fs = c["fs"]
            lh = fs * 1.3
            w = max(self._tw(l, fs, True, m) for (l, b, s, m) in lines) + 10
            h = lh * len(lines) + 4
            if c["badge"]:
                r = max(w, h) / 2 + 1
                o.append('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="#ffffff" '
                         'stroke="%s" stroke-width="2"/>' % (mx, my, r, c["color"]))
            else:
                o.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" '
                         'fill="#ffffff" fill-opacity="0.95"/>' % (
                             mx - w / 2, my - h / 2, w, h))
            y = my - h / 2 + 2
            for (line, bold, small, mono) in lines:
                o.append('<text x="%.1f" y="%.1f" font-family="%s" '
                         'font-size="%d" font-weight="bold" fill="%s" '
                         'text-anchor="middle">%s</text>' % (
                             mx, y + lh * 0.5 + fs * 0.35,
                             MONO if mono else FONT,
                             fs - 2 if small else fs,
                             "#444444" if small else c["color"],
                             escape(line)))
                y += lh
        o.append("</svg>")
        return "".join(o)


def write_drawio(diagrams, path):
    pages = "".join(d.drawio("p%d" % i) for i, d in enumerate(diagrams))
    with open(path, "w", encoding="utf-8") as f:
        f.write('<mxfile host="app.diagrams.net" type="device">%s</mxfile>'
                % pages)
