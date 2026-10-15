from PIL import Image, ImageDraw, ImageFont
import os
R='android/app/src/main/res'
def grad(sz):
    im=Image.new('RGB',(sz,sz)); px=im.load()
    a=(108,92,255); b=(0,209,178)
    for y in range(sz):
        for x in range(sz):
            t=(x*0.55+y*0.45)/sz; px[x,y]=tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
    return im
def glyph(d,sz,ox=0,oy=0,scale=1.0):
    s=sz*scale; c=sz/2
    # кадр-плёнка
    w=s*0.50; h=s*0.38
    d.rounded_rectangle([c-w/2+ox,c-h/2+oy,c+w/2+ox,c+h/2+oy],radius=s*0.07,outline=(255,255,255),width=max(2,int(s*0.035)))
    tri=[(c-s*0.07+ox,c-s*0.10+oy),(c-s*0.07+ox,c+s*0.10+oy),(c+s*0.11+ox,c+oy)]
    d.polygon(tri,fill=(255,255,255))
    # «ножницы»-срез
    d.line([(c+w/2-s*0.02+ox,c-h/2-s*0.08+oy),(c+w/2+s*0.10+ox,c-h/2+s*0.04+oy)],fill=(255,255,255),width=max(2,int(s*0.035)))
def icon(sz,round_=False):
    g=grad(sz).convert('RGBA'); m=Image.new('L',(sz,sz),0); md=ImageDraw.Draw(m)
    if round_: md.ellipse([0,0,sz-1,sz-1],fill=255)
    else: md.rounded_rectangle([0,0,sz-1,sz-1],radius=int(sz*0.22),fill=255)
    out=Image.new('RGBA',(sz,sz),(0,0,0,0)); out.paste(g,(0,0),m)
    glyph(ImageDraw.Draw(out),sz); return out
def fg(sz):
    out=Image.new('RGBA',(sz,sz),(0,0,0,0)); glyph(ImageDraw.Draw(out),sz,scale=0.62); return out
dens={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}
for k,f in dens.items():
    d=f'{R}/mipmap-{k}'; os.makedirs(d,exist_ok=True)
    s=int(48*f); icon(s).save(f'{d}/ic_launcher.png'); icon(s,True).save(f'{d}/ic_launcher_round.png')
    fg(int(108*f)).save(f'{d}/ic_launcher_foreground.png')
icon(512).save('store/icon-512.png')
# фон адаптивной иконки
open(f'{R}/values/ic_launcher_background.xml','w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#5A6BE8</color>\n</resources>\n')
# сплэш
for dd in os.listdir(R):
    if dd.startswith('drawable') and os.path.exists(f'{R}/{dd}/splash.png'):
        w,h=Image.open(f'{R}/{dd}/splash.png').size
        im=Image.new('RGB',(w,h),(13,15,20)); ic=icon(int(min(w,h)*0.28)); im.paste(ic,((w-ic.width)//2,(h-ic.height)//2),ic); im.save(f'{R}/{dd}/splash.png')
# feature graphic 1024x500
fgimg=Image.new('RGB',(1024,500),(13,15,20)); px=fgimg.load()
for y in range(500):
    for x in range(1024):
        t=x/1024; px[x,y]=(int(13+40*t),int(15+10*(1-t)),int(20+50*t))
ic=icon(260); fgimg.paste(ic,(90,120),ic)
dr=ImageDraw.Draw(fgimg)
fp='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
try: F=ImageFont.truetype(fp,78); F2=ImageFont.truetype(fp.replace('-Bold',''),34)
except: F=F2=ImageFont.load_default()
dr.text((400,160),'КлипМастер',font=F,fill=(255,255,255))
dr.text((404,270),'Обрезка · переходы · фильтры',font=F2,fill=(180,188,210))
dr.text((404,318),'Музыка · текст · MP4 720p',font=F2,fill=(180,188,210))
fgimg.save('store/feature-1024x500.png')
