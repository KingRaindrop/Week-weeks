"""Regenerate public import samples and JVM/Android parser fixtures.

Requirements: Python 3 and openpyxl (install with `python -m pip install openpyxl`).
Run from any directory: `python <repository>/tools/create-import-samples.py`.
PDF generation uses only Python's standard library and invented literal data.
"""

from pathlib import Path
from openpyxl import Workbook, load_workbook
import csv
import zlib


def synthetic_pdf():
    """Return a deterministic PDF built entirely from fictional timetable data.

    The two pages exercise compressed content, a ToUnicode character map,
    Chinese fields, disjoint/odd/even weeks, and a title continued across pages.
    No local document or font file is read or embedded.
    """
    points = [99.08 + day * 103.85 for day in range(7)]
    pages = [[], []]
    characters = set()

    def text(page, x, y, value, size=8.5):
        characters.update(value)
        encoded = value.encode('utf-16-be').hex().upper()
        pages[page].append(f'BT /FC {size} Tf 1 0 0 1 {x:.2f} {y:.2f} Tm <{encoded}> Tj ET')

    def note(page, y, value):
        # Notes are ASCII, with no personal identifiers or machine-specific data.
        pages[page].append(f'BT /FL 10 Tf 1 0 0 1 40 {y} Tm ({value}) Tj ET')

    note(0, 560, 'SYNTHETIC TIMETABLE - fictional parser fixture - page 1 of 2')
    note(0, 542, 'Every course, teacher, room and schedule entry is invented for tests.')
    for day, label in enumerate('一二三四五六日'):
        text(0, points[day], 515, '星期' + label)

    entries = [
        (0, '虚构数学', '第1-2节 1-16周', '示例楼A101', '示例教师甲'),
        (1, '虚构英语', '第3-4节 1-16周单周', '示例楼B202', '示例教师乙'),
        (2, '虚构实验', '第5-6节 1-8周,10-16周', '示例楼C303', '示例教师丙'),
    ]
    for day, name, periods_weeks, room, teacher in entries:
        top = 485 - day * 120
        for y, value in zip((top, top - 16, top - 32, top - 48),
                            (name, periods_weeks, '/场地:' + room, '/教师:' + teacher)):
            text(0, points[day], y, value)

    # Deliberately split the Thursday course at the page boundary.
    text(0, points[3], 48, '虚构设计')
    note(0, 25, 'The Thursday entry continues on page 2.')
    note(1, 560, 'SYNTHETIC TIMETABLE - fictional parser fixture - page 2 of 2')
    text(1, 78.13, 524.5, '8')  # Another column must not hide the previous-page title.
    for y, value in zip((515, 499, 483),
                        ('第7-8节 2-16周双周', '/场地:示例楼D404', '/教师:示例教师丁')):
        text(1, points[3], y, value)

    mappings = '\n'.join(f'<{ord(value):04X}> <{ord(value):04X}>' for value in sorted(characters))
    cmap = ('/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n'
            '/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n'
            '/CMapName /SyntheticTimetable def\n/CMapType 2 def\n'
            '1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n'
            f'{len(characters)} beginbfchar\n{mappings}\nendbfchar\n'
            'endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend').encode('ascii')

    def stream(data):
        compressed = zlib.compress(data, level=9)
        return (f'<< /Length {len(compressed)} /Filter /FlateDecode >>\nstream\n'.encode('ascii')
                + compressed + b'\nendstream')

    def ascii_bytes(value):
        return value.encode('ascii')

    resources = '/Resources << /Font << /FC 7 0 R /FL 11 0 R >> >>'
    objects = {
        1: b'<< /Type /Catalog /Pages 2 0 R >>',
        2: b'<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>',
        3: ascii_bytes(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] {resources} /Contents 4 0 R >>'),
        4: stream('\n'.join(pages[0]).encode('ascii')),
        5: ascii_bytes(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] {resources} /Contents 6 0 R >>'),
        6: stream('\n'.join(pages[1]).encode('ascii')),
        # Standard CJK font references allow rendering without shipping a private font.
        7: b'<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H /DescendantFonts [8 0 R] /ToUnicode 10 0 R >>',
        8: b'<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /DW 1000 /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 4 >> /FontDescriptor 9 0 R >>',
        9: b'<< /Type /FontDescriptor /FontName /STSong-Light /Flags 4 /FontBBox [-25 -254 1000 880] /ItalicAngle 0 /Ascent 880 /Descent -120 /CapHeight 880 /StemV 80 >>',
        10: stream(cmap),
        11: b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
        12: b'<< /Title (Synthetic timetable parser fixture) /Author (Fictional test data) /Creator (tools/create-import-samples.py) >>',
    }
    output = bytearray(b'%PDF-1.4\n%\xe2\xe3\xcf\xd3\n')
    offsets = [0]
    for number, body in objects.items():
        offsets.append(len(output))
        output.extend(f'{number} 0 obj\n'.encode('ascii') + body + b'\nendobj\n')
    xref = len(output)
    output.extend(f'xref\n0 {len(objects) + 1}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets[1:]:
        output.extend(f'{offset:010d} 00000 n \n'.encode('ascii'))
    output.extend((f'trailer\n<< /Size {len(objects) + 1} /Root 1 0 R /Info 12 0 R >>\n'
                   f'startxref\n{xref}\n%%EOF\n').encode('ascii'))
    return bytes(output)

root = Path(__file__).resolve().parents[1]
samples = root / 'samples'
samples.mkdir(parents=True, exist_ok=True)
unit_resources = root / 'app/src/test/resources'
unit_resources.mkdir(parents=True, exist_ok=True)
device_resources = root / 'app/src/androidTest/assets/parser-fixtures'
device_resources.mkdir(parents=True, exist_ok=True)
public_templates = root / 'docs/templates'
public_templates.mkdir(parents=True, exist_ok=True)
rows = [
    ['课程名称', '星期', '开始节次', '结束节次', '上课周', '教室', '教师'],
    ['高等数学', '周一', 1, 2, '1-16周', '教学楼A101', '张老师'],
    ['大学英语', '周二', 3, 4, '1-16周单周', '外语楼203', '李老师'],
    ['数据结构', '周三', 5, 6, '1-8周,10-16周', '实验楼301', '王老师'],
]
wb = Workbook()
ws = wb.active
ws.title = '课程列表'
for row in rows:
    ws.append(row)
path = samples / 'timetable-list-template.xlsx'
wb.save(path)
check = load_workbook(path, data_only=False)
assert check.sheetnames == ['课程列表']
assert list(check.active.values) == [tuple(row) for row in rows]
(unit_resources / path.name).write_bytes(path.read_bytes())
(device_resources / path.name).write_bytes(path.read_bytes())
(public_templates / path.name).write_bytes(path.read_bytes())
csv_path = samples / 'timetable-list-template.csv'
with csv_path.open('w', encoding='utf-8-sig', newline='') as f:
    csv.writer(f).writerows(rows)
(device_resources / csv_path.name).write_bytes(csv_path.read_bytes())
(public_templates / csv_path.name).write_bytes(csv_path.read_bytes())
pdf = synthetic_pdf()
for directory in (samples, unit_resources, device_resources):
    (directory / 'synthetic-timetable.pdf').write_bytes(pdf)
print('Created XLSX/CSV samples and test resources; reopened XLSX values and types match.')
print('Created deterministic, fictional synthetic-timetable.pdf in samples and both test-resource directories.')
