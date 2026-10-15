"""Optional fixture regeneration: python -m pip install python-pptx Pillow.
The Java build/tests use checked-in fixtures and require no Python packages.
"""
from pathlib import Path
from io import BytesIO
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.chart import XL_CHART_TYPE
from pptx.chart.data import CategoryChartData
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'examples'
OUT.mkdir(exist_ok=True)

def add_text(slide, text, x, y, w, h, font='Microsoft YaHei', size=20):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    p = shape.text_frame.paragraphs[0]
    r = p.add_run()
    r.text = text
    r.font.name = font
    r.font.size = Pt(size)
    r.font.color.rgb = RGBColor.from_string('254F3C')
    return shape

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(10), Inches(7.5)
slide = prs.slides.add_slide(prs.slide_layouts[6])
slide.background.fill.solid()
slide.background.fill.fore_color.rgb = RGBColor.from_string('F4F6F0')
add_text(slide, 'PPT 转换示例 / 4:3', .6, .35, 8.8, .7, '微软雅黑', 28)
add_text(slide, 'Microsoft YaHei → Arial · 图片、表格、图表仍可编辑', .6, 1.05, 8.8, .6, size=14)
add_text(slide, 'Keep this font: Times New Roman', .6, 6.75, 8, .4, 'Times New Roman', 12)
image = Image.new('RGB', (320, 180), '#DCE8D4')
draw = ImageDraw.Draw(image)
draw.ellipse((90, 20, 230, 160), fill='#427052')
draw.rectangle((15, 80, 75, 150), fill='#A6BD84')
buf = BytesIO()
image.save(buf, format='PNG')
buf.seek(0)
slide.shapes.add_picture(buf, Inches(.6), Inches(1.9), width=Inches(3.2), height=Inches(1.8))
table = slide.shapes.add_table(3, 3, Inches(4.2), Inches(1.9), Inches(5.2), Inches(1.8)).table
for row, values in enumerate([['项目', '转换前', '转换后'], ['比例', '4:3', '16:9'], ['高度', '7.5 in', '7.5 in']]):
    for col, text in enumerate(values):
        cell = table.cell(row, col)
        cell.text = text
        for p in cell.text_frame.paragraphs:
            for run in p.runs:
                run.font.name = 'Microsoft YaHei'
                run.font.size = Pt(12)
data = CategoryChartData()
data.categories = ['A', 'B', 'C']
data.add_series('Editable chart', (3, 7, 5))
chart = slide.shapes.add_chart(XL_CHART_TYPE.COLUMN_CLUSTERED, Inches(.6), Inches(4), Inches(5), Inches(2.5), data).chart
chart.has_legend = True
chart.legend.font.name = 'Microsoft YaHei'
slide.notes_slide.notes_text_frame.text = '演讲备注：图片、表格、图表和组合形状测试。'
for p in slide.notes_slide.notes_text_frame.paragraphs:
    for r in p.runs: r.font.name = '微软雅黑'
group = slide.shapes.add_group_shape()
for x, color in [(6.2, 'A6BD84'), (7.7, '427052')]:
    shape = group.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(4.5), Inches(1.3), Inches(1.3))
    shape.fill.solid(); shape.fill.fore_color.rgb = RGBColor.from_string(color)
add_text(slide, '组合对象：保持子对象坐标', 6.1, 6, 3.4, .45, 'Arial', 12)
slide2 = prs.slides.add_slide(prs.slide_layouts[1])
slide2.shapes.title.text = '继承版式与母版的占位符'
slide2.placeholders[1].text = '标题、正文和母版元素保持原高度。\n请在 PowerPoint 中检查字体回退及对象编辑。'
for s in slide2.shapes:
    if s.has_text_frame:
        for p in s.text_frame.paragraphs:
            for r in p.runs: r.font.name = 'Microsoft YaHei'
prs.save(OUT / 'sample-4x3.pptx')
prs.slide_width = Inches(40 / 3)
prs.save(OUT / 'sample-16x9.pptx')
prs.slide_width = Inches(13 * 7.5 / 9)
prs.slides[0].shapes[0].text_frame.paragraphs[0].runs[0].text = 'PPT 转换示例 / 1.4444'
prs.save(OUT / 'sample-company-1.4444.pptx')
# Keep one corrupt input for failure-isolation tests.
(OUT / 'invalid.pptx').write_bytes(b'This is deliberately not a PPTX file.\n')
print('Generated sample-4x3.pptx, sample-16x9.pptx and invalid.pptx')
