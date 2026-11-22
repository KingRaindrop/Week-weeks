# 导入模板 / Import templates

- [XLSX 模板](timetable-list-template.xlsx)
- [CSV 模板](timetable-list-template.csv)

以上为列表模板，每行一门课，包含课程名、星期、节次、周次、教室和教师。所有内容均为虚构示例，请替换为自己的课程。格式说明见[列表导入说明](../IMPORT-PARSERS-20261114.md)；也可以直接按[周课表示例](../IMPORT-PARSERS-20261119.md)整理。

For a weekly grid with weekday headings instead, see the [grid example](../IMPORT-PARSERS-20261119.md). Each row in the two list templates describes one course. All sample courses, rooms and teacher names are fictional. Replace them with your own data before importing.

模板和解析器测试夹具由 [create-import-samples.py](../../tools/create-import-samples.py) 生成。需要 Python 3 和 `openpyxl`，应用构建本身不需要 Python：

```powershell
python -m pip install openpyxl
python tools/create-import-samples.py
```

PDF 测试夹具仅由固定虚构文本生成，不读取私人课表或本机字体；它用于自动测试，不是通用 PDF 导入格式承诺。
