# Generere diagrammer og dokument på nytt

Diagrammene er definert i `diagrams.py`. Samme geometri gir både draw.io-sidene og PNG-filene.

```bash
python3 -m pip install --target /tmp/pylib python-docx resvg-py
cd docs/tools
PYTHONPATH=/tmp/pylib python3 render.py ../build          # PNG + .drawio
PYTHONPATH=/tmp/pylib python3 builddoc.py ../build        # Word-dokumentet
```

Du kan også redigere `IN5020_A2_diagrammer.drawio` direkte på diagrams.net.
