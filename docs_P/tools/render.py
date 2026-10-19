import os
import sys

import resvg_py

from diagrams import build
from dsl import write_drawio

out_dir = sys.argv[1]
png_dir = os.path.join(out_dir, "png")
os.makedirs(png_dir, exist_ok=True)

diagrams = build()
only = sys.argv[2:]  # optional: render only these keys
for d in diagrams:
    if only and d.key not in only:
        continue
    png = resvg_py.svg_to_bytes(svg_string=d.svg(), zoom=2)
    with open(os.path.join(png_dir, d.key + ".png"), "wb") as f:
        f.write(bytes(png))
    print("rendered", d.key)

write_drawio(diagrams, os.path.join(out_dir, "IN5020_A2_diagrammer.drawio"))
print("wrote drawio with", len(diagrams), "pages")
