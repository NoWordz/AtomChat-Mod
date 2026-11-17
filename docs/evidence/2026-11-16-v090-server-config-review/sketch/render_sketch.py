"""Editable design sketch, not production UI. Run with Python, fontTools, CairoSVG, Pillow.

Uses the project's real bundled regular/bold typefaces and existing icon paths.
Dimensions are UiTokens layout units: SCALE=1.25, panel 600x960.
Text is outlined for a reliable standalone PNG/SVG; wording stays editable here.
"""
from pathlib import Path
import base64
import html
import json
import re

import cairosvg
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[3]
LIB = Path("D:/AtomLib")
FONT_ROOT = LIB / "runtime/src/main/resources/com/atom/lib/fonts"
REGULAR = FONT_ROOT / "bundled-default.otf"
BOLD = FONT_ROOT / "bundled-default-bold.otf"
FONTS = [TTFont(REGULAR), TTFont(BOLD)]
# FontManager.mediumWeight uses the bundled variable face at wght=500.
GLYPHS = [FONTS[0].getGlyphSet(location={'wght': 500}), FONTS[1].getGlyphSet()]
CMAPS = [f.getBestCmap() for f in FONTS]
UNITS = [f['head'].unitsPerEm for f in FONTS]
ACCENT = "#4a90e2"
TEXT = "#ffffff"
SUB = "#a7aeba"
PANEL = "#101418"
CARD = "#12151a"
RIM = "#373b40"
DANGER = "#eb4034"
PAD = 17.5  # UiTokens.SETTINGS_ROW_PAD=s(14)
CARD_R = 20  # UiTokens.cardRadius()=s(16)
CHROME_R = 22.5  # UiTokens.chromeRadius()=s(18)
SCALE = 1.25
def s(v): return v * SCALE

# UiLayout.cardColumnX / cardColumnW, including ROW_CLIP_INSET=s(6.4).
X, W = 20+s(12)+s(6.4), 600-s(12)*2-s(6.4)*2
TITLE, DESC = 18.75, 15  # UiTokens.SETTINGS_TILE_TITLE / SUB
SHAPES = {}


def chip_rect(index, count=3, panel_width=600):
    """UiLayout chipBar + chipRect, no independent pill width/height/step."""
    bar_x = 20 + s(12)
    bar_y = 20 + s(14) + s(44) + s(8)
    bar_w = panel_width - s(12)*2
    bar_h = s(26) + s(8)*2
    cell_w, inset = bar_w/count, s(8)
    return (bar_x+cell_w*index+inset, bar_y+inset,
            cell_w-inset*2, bar_h-inset*2)


def centered_baseline(center, size, bold=False):
    # SkiaFontRenderer.centerBaselineY: center + capHeight/2.
    i=int(bold)
    return center + FONTS[i]['OS/2'].sCapHeight / UNITS[i] * size/2


def java_string(path, name):
    source = path.read_text(encoding="utf-8")
    match = re.search(r'\b' + name + r'\s*=\s*(.*?);', source, re.S)
    if not match:
        raise ValueError(f"Missing icon: {name}")
    return ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', match[1]))


glyph_source = PROJECT / "shared/src/main/java/com/atom/chat/ui/SettingsGroupGlyphs.java"
draw_source = PROJECT / "shared/src/main/java/com/atom/chat/render/DrawPrimitives.java"
app_source = LIB / "ui/src/main/java/com/atom/lib/design/atomchat/AppIcons.java"
for key, source, name in [
    ('image', draw_source, 'ICON_IMAGE_SVG'), ('emoji', draw_source, 'ICON_EMOJI_SVG'),
    ('phrase', draw_source, 'ICON_PHRASE_SVG'), ('back', app_source, 'ICON_BACK_SVG'),
    ('profile', app_source, 'ICON_TAB_PROFILE_SVG'), ('clock', app_source, 'ICON_CLOCK_SVG'),
    ('list', glyph_source, 'MESSAGE_LIST'), ('layout', glyph_source, 'LAYOUT'),
    ('trash', glyph_source, 'TRASH'), ('plug', glyph_source, 'PLUG')]:
    SHAPES[key] = java_string(source, name)


def text_width(s, size, bold=False):
    i = int(bold)
    return sum(GLYPHS[i][CMAPS[i].get(ord(c), '.notdef')].width for c in s) * size / UNITS[i]


class Sheet:
    def __init__(self, title, active=None, nested=False, panel_width=600, light=False, no_navigation=False):
        self.panel_width, self.canvas_width, self.light = panel_width, panel_width+40, light
        self.x, self.w = X, panel_width-s(12)*2-s(6.4)*2
        self.parts = [f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 {self.canvas_width} 1000" width="{self.canvas_width}" height="1000" role="img" aria-label="{title}，设计草图">
<style>path,image{{pointer-events:none}}</style>''']
        self.rect(0, 0, self.canvas_width, 1000, 0, '#637b8b')
        for spread, opacity in [(10,.035), (6,.06), (2,.15)]:
            self.rect(20-spread, 24-spread, panel_width+spread*2, 960+spread*2, 30+spread, '#000', opacity=opacity)
        self.rect(20, 20, panel_width, 960, 28, PANEL, '#272b30', 4)
        self.rect(35, 37.5, panel_width-30, 55, CHROME_R, '#0e1115', '#1b2025', .75)
        self.icon('back', 48, 51, 28)
        self.text(title, self.canvas_width/2, 74, 23.75, bold=True, align='center')
        self.text('20:06', panel_width-19, 73, 20, align='right')
        if no_navigation: return
        if not nested:
            self.chips(active)
        else:
            self.text('服务端配置 / ' + ('消息模板' if active == 2 else '资源包'), X+PAD, 129, DESC, SUB)

    def color(self, color):
        if not self.light: return color
        # Existing ThemeService.MINIMAL palette; field shades are design proposals.
        return {TEXT:'#151a22',SUB:'#596273',PANEL:'#f7f8fa',CARD:'#ffffff',
                ACCENT:'#2563d8',RIM:'#d4d8df','#272b30':'#d4d8df',
                '#0e1115':'#ffffff','#1b2025':'#e7eaf0','#14181e':'#ffffff',
                '#292e36':'#d4d8df','#1b2027':'#f2f4f7','#575e67':'#a4acb7',
                '#252b33':'#eceef2','#616873':'#b8bec8','#646b75':'#9aa2af',
                '#252d38':'#e7edf5','#171b20':'#eff1f5','#343b45':'#d9dee6',
                '#66707c':'#969fab'}.get(color,color)

    def add(self, s):
        self.parts.append(s)

    def rect(self, x, y, w, h, r, fill, stroke=None, sw=1, opacity=1, extra=''):
        fill, stroke = self.color(fill), self.color(stroke)
        self.add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" opacity="{opacity}"' + (f' stroke="{stroke}" stroke-width="{sw}"' if stroke else '') + f' {extra}/>')

    def line(self, x1, y1, x2, y2, color=RIM, width=1):
        color=self.color(color)
        self.add(f'<path d="M{x1} {y1}H{x2}" fill="none" stroke="{color}" stroke-width="{width}"/>' if y1==y2 else f'<path d="M{x1} {y1}L{x2} {y2}" stroke="{color}" stroke-width="{width}"/>')

    def text(self, s, x, baseline, size=TITLE, color=TEXT, bold=False, align='left'):
        color=self.color(color)
        i = int(bold)
        scale = size / UNITS[i]
        width = text_width(s, size, bold)
        if align == 'right': x -= width
        elif align == 'center': x -= width/2
        self.add(f'<g fill="{color}" aria-label="{html.escape(s, quote=True)}"><title>{html.escape(s)}</title>')
        for char in s:
            name = CMAPS[i].get(ord(char), '.notdef')
            pen = SVGPathPen(GLYPHS[i])
            GLYPHS[i][name].draw(pen)
            self.add(f'<path transform="translate({x:.4f} {baseline}) scale({scale:.7f} {-scale:.7f})" d="{pen.getCommands()}"/>')
            x += GLYPHS[i][name].width * scale
        self.add('</g>')

    def wrap(self, s, x, y, width, size=DESC, color=SUB, line_height=22):
        line=''
        for c in s:
            if c == '\n' or text_width(line+c, size) > width:
                self.text(line, x, y, size, color)
                y += line_height
                line = '' if c == '\n' else c
            else: line += c
        if line: self.text(line, x, y, size, color)
        return y

    def icon(self, key, x, y, size=22.5, color=TEXT):
        color=self.color(color)
        # Tight path bounds, same fit-by-longest-edge rule as DrawPrimitives.
        from fontTools.svgLib.path import parse_path
        bounds=BoundsPen(None)
        parse_path(SHAPES[key], bounds)
        left, top, right, bottom = bounds.bounds
        scale=size/max(right-left, bottom-top)
        tx=x+(size-(right-left)*scale)/2-left*scale
        ty=y+(size-(bottom-top)*scale)/2-top*scale
        stroke=1.875 if size<=20 else 1.875+(size-20)/20*1.25  # UiTokens.iconStroke(size)
        self.add(f'<path d="{SHAPES[key]}" transform="translate({tx} {ty}) scale({scale})" fill="none" stroke="{color}" stroke-width="{stroke/scale}" stroke-linecap="round" stroke-linejoin="round"/>')

    def chevron(self, x, y, down=False, color=TEXT, size=6):
        color=self.color(color)
        d=f'M{x-size} {y-size/2}L{x} {y+size/2}L{x+size} {y-size/2}Z' if down else f'M{x-size/2} {y-size}L{x+size/2} {y}L{x-size/2} {y+size}Z'
        self.add(f'<path d="{d}" fill="{color}"/>')

    def group(self, y, title, icon):
        x,w=self.x,self.w
        self.icon(icon, x+PAD, y+6, 22.5)
        textx=x+PAD+22.5+12.5
        # Same subtle dot trail and under-rule as SettingsSectionPage.drawLabel.
        for row in range(5):
            for col in range(72):
                px=textx+col*4.5
                if px > x+w-PAD-25: break
                alpha=.14*(1-col/75)*(1-abs(row-2)/4)
                self.add(f'<circle cx="{px}" cy="{y+9+row*4}" r=".65" fill="{self.color(SUB)}" opacity="{alpha:.3f}"/>')
        self.text(title, textx, y+25, TITLE, bold=True)
        self.chevron(x+w-PAD-4, y+18, True)
        self.line(textx, y+37, x+w-PAD, y+37, '#616873', .8)

    def card(self, y, h, hover=False):
        x,w=self.x,self.w
        self.rect(x, y+2, w, h, CARD_R, '#000000', opacity=.14)
        self.add('<g class="card-sketch">')
        self.rect(x, y, w, h, CARD_R, CARD, RIM, 1.25)
        self.rect(x, y, w, h, CARD_R, ACCENT, opacity=.10 if hover else 0)
        self.add('</g>')

    def divider(self, y):
        self.line(self.x+PAD, y, self.x+self.w-PAD, y)

    def field(self, x, y, w, h, value, focus=False, size=18.75, state='rest'):
        if focus: state='focus'
        fill={'hover':'#252d38','disabled':'#171b20'}.get(state,'#1b2027')
        rim={'focus':ACCENT,'error':DANGER,'disabled':'#343b45'}.get(state,'#575e67')
        ink='#66707c' if state=='disabled' else TEXT
        self.rect(x, y, w, h, 10, fill, rim, 1.5 if state in ('focus','error') else .9)
        self.text(value, x+12.5, y+(h-size)/2+size*.82, size, ink)
        if focus:
            caret=x+12.5+text_width(value,size)+2
            self.line(caret, y+9, caret, y+h-9, ACCENT, 1.5)

    def numeric(self, y, title, desc, value, unit, focus=False):
        x,w=self.x,self.w
        self.text(title, x+PAD, y+28, TITLE)
        self.text(desc, x+PAD, y+50, DESC, SUB)
        self.field(x+w-154, y+15, 94, 40, value, focus)
        self.text(unit, x+w-47, y+41, DESC, SUB)

    def toggle(self, y, title, desc):
        self.text(title, X+PAD, y+28, TITLE)
        self.text(desc, X+PAD, y+50, DESC, SUB)
        self.rect(X+W-PAD-50, y+22, 50, 27.5, 13.75, ACCENT)
        self.rect(X+W-PAD-25, y+24.5, 22.5, 22.5, 11.25, TEXT)

    def button(self, x, y, w, label, h=36, primary=False, danger=False):
        fill=ACCENT if primary else '#252b33'
        color=DANGER if danger else '#FFFFFF' if primary else TEXT
        self.add('<g class="button-sketch">')
        self.rect(x,y,w,h,11.25,fill)
        self.text(label,x+w/2,y+(h-17)/2+14,17,color,align='center')
        self.add('</g>')

    def chips(self, active):
        for i, label in enumerate(['托管','资源包','消息模板']):
            x,y,w,h=chip_rect(i,panel_width=self.panel_width)
            radius=max(0,min(CHROME_R-s(8),w/2,h/2))
            if i==active: self.rect(x,y,w,h,radius,ACCENT)
            size=s(12)
            while size>s(10) and any(text_width(t,size,True)>w-s(10) for t in ['托管','资源包','消息模板']):
                size-=s(.5)
            baseline=centered_baseline(y+h/2,size,i==active)
            self.text(label,x+w/2,baseline,size,'#FFFFFF' if i==active else SUB,bold=i==active,align='center')

    def footer(self, hint='有未保存的更改', action='保存'):
        self.add(f'<circle cx="{self.x+PAD+3}" cy="868" r="3" fill="{self.color(ACCENT)}"/>')
        self.text(hint, self.x+PAD+15, 873, DESC, SUB)
        # UiLayout ROOT bottom + BottomTabBar internal TAB_EDGE_PAD=s(8).
        self.rect(35,897.5,self.panel_width-30,65,CHROME_R,'#14181e','#292e36',.8)
        self.button(45,907.5,self.panel_width-50,action,45,primary=True)

    def image(self, path, x, y, w, h):
        raw=base64.b64encode(path.read_bytes()).decode()
        self.add(f'<image x="{x}" y="{y}" width="{w}" height="{h}" preserveAspectRatio="xMidYMid meet" xlink:href="data:image/jpeg;base64,{raw}"/>')

    def finish(self, name):
        self.add('</svg>')
        svg='\n'.join(self.parts)
        path=ROOT/(name+'.svg')
        path.write_text(svg,encoding='utf-8')
        cairosvg.svg2png(bytestring=svg.encode(), write_to=str(ROOT/(name+'.png')),output_width=self.canvas_width*2,output_height=2000)
        return name


def hosting():
    p=Sheet('AtomChat 服务端配置',0)
    p.group(184,'图片与头像','image')
    p.card(231,70)
    p.toggle(231,'启用托管','允许玩家上传和查看聊天图片、头像')
    p.card(311,280)
    p.numeric(311,'单文件大小','限制每次上传的文件大小','2048','KiB',True)
    p.divider(381)
    # A hover wash example. It illustrates the intended family state, not runtime proof.
    p.rect(X+1,382,W-2,69,0,ACCENT,opacity=.10)
    p.numeric(381,'媒体库容量','聊天图片与其他共享媒体','512','MiB')
    p.divider(451)
    p.numeric(451,'头像库容量','用于头像托管的独立空间','64','MiB')
    p.divider(521)
    p.numeric(521,'个人资料图片库','存放个人资料中上传的图片','128','MiB')
    p.group(607,'保留与频率','clock')
    p.card(654,140)
    p.numeric(654,'保留时间','设为 0 时永久保留','7','天')
    p.divider(724)
    p.numeric(724,'上传间隔','同一玩家连续上传的最短间隔','3000','ms')
    p.footer()
    return p.finish('01-hosting')


def resources(sample):
    p=Sheet('AtomChat 服务端配置',1)
    p.group(184,'基础信息','layout')
    p.card(231,300)
    p.toggle(231,'下发聊天资源包','保存后向允许接收的在线玩家同步')
    p.divider(301)
    p.text('服务器显示名',X+PAD,324,TITLE)
    p.field(X+PAD,337,W-PAD*2,36,'我们的服务器',True)
    p.text('留空时使用服务器 MOTD',X+PAD,395,DESC,SUB)
    p.divider(400)
    p.text('容量上限',X+PAD,429,TITLE)
    p.text('按客户端可接收的大小校验',X+PAD,450,DESC,SUB)
    p.field(X+W-154,414,94,36,'16')
    p.text('MiB',X+W-47,438,DESC,SUB)
    p.divider(461)
    p.numeric(461,'文件数量上限','限制资源包中可包含的文件数','200','个')
    p.group(545,'下发内容','emoji')
    p.card(592,166)
    p.text('表情包',X+PAD,621,TITLE)
    p.text('1 张 · 新增内容待保存',X+PAD,643,DESC,SUB)
    p.button(X+W-PAD-112,605,112,'导入图片',36,primary=True)
    p.rect(X+PAD,658,78,78,10,'#fff')
    p.image(sample,X+PAD+3,661,72,72)
    p.text('曼波.jpg',X+PAD+94,681,17)
    p.text('待导入',X+PAD+94,703,DESC,SUB)
    p.button(X+PAD+94,715,70,'预览',28)
    p.button(X+PAD+174,715,70,'移除',28,danger=True)
    p.card(771,66,hover=True)
    p.icon('phrase',X+PAD,791,25)
    p.text('常用语',X+PAD+40,798,TITLE)
    p.text('3 条 · 编辑内容与显示顺序',X+PAD+40,821,DESC,SUB)
    p.chevron(X+W-PAD-5,804)
    p.footer('有未保存的更改 · 新增 1 张表情包')
    return p.finish('02-resources')


def phrase_list():
    p=Sheet('常用语',1,nested=True)
    p.group(151,'常用语列表','phrase')
    p.text('3 / 20 条',X+PAD,225,DESC,SUB)
    p.button(X+W-PAD-98,200,98,'＋ 新增',36,primary=True)
    p.card(255,264)
    entries=[('欢迎来到服务器！','第一条'),('稍等，我马上回来。','第二条'),('谢谢，一起玩吧！','第三条')]
    for i,(text,label) in enumerate(entries):
        y=255+i*88
        if i==1: p.rect(X+1,y,W-2,88,0,ACCENT,opacity=.10)
        p.text(text,X+PAD,y+35,TITLE)
        p.text('点击编辑',X+PAD,y+60,DESC,SUB)
        for j in range(3):
            bx=X+W-PAD-116+j*40
            p.rect(bx,y+26,32,36,9,'#232a32')
            if j<2:
                p.chevron(bx+16,y+44,True,color='#646b75' if (i==0 and j==0) or (i==2 and j==1) else TEXT,size=5)
                if j==0:
                    # Reverse the down arrow by painting the up triangle over its face.
                    p.rect(bx+6,y+34,20,20,0,'#232a32')
                    p.add(f'<path d="M{bx+11} {y+47}L{bx+16} {y+41}L{bx+21} {y+47}Z" fill="{"#646b75" if i==0 else TEXT}"/>')
            else: p.icon('trash',bx+8,y+35,16,DANGER)
        if i<2: p.divider(y+88)
    p.wrap('使用箭头调整顺序。进入单条编辑后，返回列表会保留改动。',X+PAD,553,W-PAD*2)
    p.footer('有未保存的更改 · 资源包草稿',action='返回资源包')
    return p.finish('03-phrases')


def template_edit():
    p=Sheet('编辑消息模板',2,nested=True)
    p.group(151,'模板设置','list')
    p.card(198,327)
    p.text('模板名称',X+PAD,226,TITLE)
    p.field(X+PAD,239,W-PAD*2,40,'跨服公共聊天')
    p.text('消息类型',X+PAD,307,TITLE)
    for i,t in enumerate(['公共消息','收到私聊','发出私聊']):
        p.button(X+PAD+i*172,320,162,t,34,primary=i==0)
    p.text('消息格式',X+PAD,386,TITLE)
    p.field(X+PAD,398,W-PAD*2,48,'[{channel}] {sender}: {message}',True,size=17)
    placeholders=[('玩家名',108),('正文',86),('频道',86),('UUID',90),('接收者',108)]
    xx=X+PAD
    for label,ww in placeholders:
        p.button(xx,458,ww,label,30)
        xx += ww+7
    p.text('点击字段插入占位符',X+PAD,511,DESC,SUB)
    p.group(538,'样例与提取预览','image')
    p.card(585,96)
    p.text('粘贴一条完整消息',X+PAD,613,TITLE)
    p.field(X+PAD,625,W-PAD*2,40,'[全服] Alice: 大家晚上好',size=17)
    p.card(693,139)
    p.text('提取成功',X+PAD,721,TITLE,ACCENT)
    p.text('频道',X+PAD,749,DESC,SUB)
    p.text('全服',X+PAD+70,749,17)
    p.text('玩家名',X+PAD+215,749,DESC,SUB)
    p.text('Alice',X+PAD+290,749,17)
    p.text('正文',X+PAD,776,DESC,SUB)
    p.text('大家晚上好',X+PAD+70,776,17)
    p.text('身份依据：仅提取名字，尚未获得 UUID',X+PAD,807,DESC,SUB)
    p.footer('完成返回列表 · 在主配置页保存后生效',action='完成')
    return p.finish('04-template')


def phrase_edit():
    p=Sheet('编辑常用语',1,nested=True)
    p.group(151,'常用语内容','phrase')
    p.card(198,239)
    p.text('常用语文本',X+PAD,228,TITLE)
    p.text('点击后填入聊天输入栏',X+PAD,251,DESC,SUB)
    p.rect(X+PAD,268,W-PAD*2,114,10,'#1b2027',ACCENT,1.5)
    p.text('稍等，我马上回来。',X+PAD+12.5,300,20)
    p.line(X+PAD+12.5+text_width('稍等，我马上回来。',20)+2,280,X+PAD+12.5+text_width('稍等，我马上回来。',20)+2,305,ACCENT,1.5)
    p.text('9 / 200',X+W-PAD,411,DESC,SUB,align='right')
    p.wrap('完成或返回均保留草稿，回到服务端配置页保存后统一下发。',X+PAD,471,W-PAD*2)
    p.footer('完成返回列表 · 在主配置页保存后生效',action='完成')
    return p.finish('05-phrase-edit')


def template_list():
    p=Sheet('AtomChat 服务端配置',2)
    p.group(184,'消息模板','list')
    p.text('3 条 · 2 条启用',X+PAD,254,DESC,SUB)
    p.button(X+W-PAD-112,231,112,'＋ 新增',36,primary=True)
    p.card(285,264)
    entries=[('跨服公共聊天','公共消息 · 玩家名、正文、频道',True),
             ('收到私聊','收到私聊 · 发送者、正文',True),
             ('发出私聊','发出私聊 · 接收者、正文',False)]
    for i,(name,summary,enabled) in enumerate(entries):
        y=285+i*88
        p.text(name,X+PAD,y+34,TITLE)
        p.text(summary,X+PAD,y+60,DESC,SUB)
        p.button(X+W-210,y+27,70,'编辑',34)
        tx=X+W-120
        p.rect(tx,y+30,50,27.5,13.75,ACCENT if enabled else '#343b45')
        p.rect(tx+(25 if enabled else 2.5),y+32.5,22.5,22.5,11.25,TEXT)
        p.rect(X+W-PAD-32,y+27,32,34,9,'#252b33')
        p.icon('trash',X+W-PAD-24,y+35,16,DANGER)
        if i<2: p.divider(y+88)
    p.wrap('新增、编辑、启用和删除均保留在草稿中，保存后统一生效。',X+PAD,583,W-PAD*2)
    p.wrap('模板用于解释已有消息，不会改变聊天插件的输出。',X+PAD,627,W-PAD*2)
    p.footer('有未保存的更改 · 消息模板草稿')
    return p.finish('06-template-list')


def states():
    p=Sheet('输入与模板状态',no_navigation=True)
    p.text('静态状态说明，未连接真实解析器',X+PAD,129,DESC,SUB)
    p.group(151,'输入框状态','layout')
    p.card(198,350)
    rows=[('静止','轻底与细边界','2048','rest'),
          ('悬停','底色柔和提亮','2048','hover'),
          ('焦点','强调色边框与光标','2048','focus'),
          ('错误','请输入 1–16384','20000','error'),
          ('禁用','暂时不可编辑','2048','disabled')]
    for i,(title,description,value,state) in enumerate(rows):
        y=198+i*70
        p.text(title,X+PAD,y+28,TITLE)
        p.text(description,X+PAD,y+50,DESC,DANGER if state=='error' else SUB)
        p.field(X+W-PAD-235,y+15,235,40,value,focus=state=='focus',state=state)
        if i<4: p.divider(y+70)
    p.group(563,'模板预览状态','list')
    p.card(610,100)
    p.text('无匹配',X+PAD,640,TITLE,DANGER)
    p.wrap('样例不符合当前格式，请检查分隔符和字段位置。',X+PAD,666,W-PAD*2)
    p.text('不显示此前提取的玩家名与正文',X+PAD,692,DESC,SUB)
    p.card(721,110)
    p.text('匹配有歧义',X+PAD,751,TITLE,DANGER)
    p.wrap('样例存在多种字段拆分，请补充分隔信息。',X+PAD,777,W-PAD*2)
    p.text('不选择任一结果作为玩家身份',X+PAD,808,DESC,SUB)
    p.wrap('本页为设计状态示意，不是交互或解析测试结果。',X+PAD,875,W-PAD*2)
    return p.finish('07-states')


def compact_light():
    p=Sheet('服务端配置',0,panel_width=360,light=True)
    x,w=p.x,p.w
    p.add('<desc>360 单位窄面板，ThemeService.MINIMAL 浅色，托管设置节选。</desc>')
    p.group(184,'图片与头像','image')
    p.card(231,124)
    p.text('启用托管',x+PAD,259,TITLE)
    p.wrap('允许玩家上传和查看聊天图片、头像',x+PAD,283,w-PAD*2)
    tx=x+w-PAD-50
    p.rect(tx,310,50,27.5,13.75,ACCENT)
    p.rect(tx+25,312.5,22.5,22.5,11.25,'#FFFFFF')
    p.card(367,222)
    p.numeric(367,'单文件大小','每次上传的文件大小','2048','KiB',True)
    p.divider(441)
    p.numeric(441,'媒体库容量','共享聊天图片','512','MiB')
    p.divider(515)
    p.numeric(515,'个人资料图片库','个人资料图片','128','MiB')
    p.group(607,'保留与频率','clock')
    p.card(654,140)
    p.numeric(654,'保留时间','0 为永久保留','7','天')
    p.divider(724)
    p.numeric(724,'上传间隔','连续上传的最短间隔','3000','ms')
    p.footer()
    return p.finish('08-compact-light')


def main():
    (ROOT/'assets').mkdir(parents=True,exist_ok=True)
    sample=ROOT/'assets/sample-emote.jpg'
    original=Path('D:/Myworld/.minecraft/versions/1.21.1-CCB/atomchat/client/emotes/07e149eaab5d761172daa7cd21a9badb2005738004.jpg')
    if not sample.exists():
        # Local test meme, shown as a sample only. Does not modify the actual pack.
        with Image.open(original) as im:
            im.thumbnail((320,320))
            im.convert('RGB').save(sample,quality=90)
    names=[hosting(),resources(sample),phrase_list(),template_list(),template_edit(),phrase_edit(),states(),compact_light()]
    overview=Image.new('RGB',(1320,3250),'#637b8b')
    draw=ImageDraw.Draw(overview)
    font=ImageFont.truetype(str(BOLD),25)
    desc=ImageFont.truetype(str(REGULAR),18)
    desc.set_variation_by_axes([500])
    draw.text((28,20),'AtomChat 服务端配置 · 草图',font=font,fill='white')
    draw.text((28,57),'复用设置族群；内容、数据和状态为演示，尚未改运行代码。',font=desc,fill='#e1e7ec')
    labels=['托管 · 保存全部草稿','资源包 · 保存全部草稿','常用语列表 · 返回资源包',
            '消息模板列表 · 保存全部草稿','消息模板编辑 · 完成回列表','常用语编辑 · 完成回列表',
            '输入与模板状态示意','窄面板浅色 · 托管节选']
    for i,name in enumerate(names[:6]):
        x=10+(i%2)*650
        y=100+(i//2)*1050
        draw.text((x+22,y),labels[i],font=desc,fill='white')
        with Image.open(ROOT/(name+'.png')) as im:
            overview.paste(im.resize((640,1000),Image.Resampling.LANCZOS),(x,y+25))
    overview.save(ROOT/'00-overview.png')
    links=''.join(f'<a href="#{n}">{label}</a>' for n,label in zip(names,labels))
    figures=''.join(f'<section id="{n}" style="max-width:{400 if n=="08-compact-light" else 640}px"><h2>{label}</h2><img src="{n}.png" alt="{label}，静态草图"><a href="{n}.png">PNG 原图</a> · <a href="{n}.svg">可编辑 SVG</a></section>' for n,label in zip(names,labels))
    (ROOT/'index.html').write_text(f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>AtomChat 服务端配置草图</title><style>body{{margin:0;background:#637b8b;color:white;font:16px system-ui}}header{{padding:18px;background:#101418}}nav{{display:flex;gap:18px;flex-wrap:wrap}}a{{color:#c9e0ff}}main{{display:flex;gap:20px;flex-wrap:wrap;padding:20px}}section{{max-width:640px}}img{{width:100%;height:auto}}h2{{font-size:18px}}p{{margin:8px 0 14px}}</style><header><strong>AtomChat 服务端配置草图 · 审查修订</strong><p>本页仅展示静态图片，所有控件和提取结果为示意；没有编辑、解析或保存功能。未改运行代码。</p><nav>{links}</nav></header><main>{figures}</main></html>''',encoding='utf-8')
    print(json.dumps({'pngs':[str(ROOT/'00-overview.png')]+[str(ROOT/(n+'.png')) for n in names],'sources':[str(ROOT/'render_sketch.py'),str(ROOT/'index.html')]+[str(ROOT/(n+'.svg')) for n in names]},ensure_ascii=False,indent=2))


if __name__ == '__main__': main()
