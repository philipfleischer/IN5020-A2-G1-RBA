import os
import sys

from diagrams_en import build

out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)

for d in build():
    with open(os.path.join(out_dir, d.key + ".svg"), "w", encoding="utf-8") as f:
        f.write(d.svg())
    print("wrote", d.key + ".svg")
