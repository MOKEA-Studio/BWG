from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
source=Path(__file__).resolve().parent/'spawn-plan.json';rows=json.loads(source.read_text());blocks={(v['x'],v['y'],v['z']):v['material']for v in rows}
width,height=1500,1120
img=Image.new('RGB',(width,height),'#eef3f5');draw=ImageDraw.Draw(img)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
F=lambda size:ImageFont.truetype(font,size);B=lambda size:ImageFont.truetype(bold,size)
draw.rounded_rectangle((28,24,width-28,height-24),radius=32,fill='#ffffff')
draw.text((76,57),'BLACKWIKISMP',font=B(25),fill='#067e91');draw.text((width-183,54),'2.5',font=B(39),fill='#067e91')
draw.text((76,107),'100 x 100  GARDEN SPAWN',font=B(36),fill='#183941')
draw.text((78,161),'Central menu NPC  /  12 trees  /  4 fountains',font=F(21),fill='#61777e')
# Render exposed faces of the exact generated blueprint, without any invented structures.
colors={'DEEPSLATE_BRICKS':'#41505a','ANDESITE':'#92998f','STONE_BRICKS':'#a9b0a4','MOSS_BLOCK':'#719c4b','POLISHED_DEEPSLATE':'#46525b','CHISELED_STONE_BRICKS':'#9fa99d','SMOOTH_QUARTZ':'#eee8dc','QUARTZ_BRICKS':'#ded7c8','CALCITE':'#f7f0df','POLISHED_BLACKSTONE':'#38464a','AZALEA_LEAVES':'#588d43','FLOWERING_AZALEA_LEAVES':'#759848','PRISMARINE_BRICKS':'#70b7b3','SEA_LANTERN':'#ddf3dc','SMOOTH_QUARTZ_SLAB':'#eee7d9','WATER':'#4fa9d5','CHISELED_QUARTZ_BLOCK':'#ddd4c8','CHERRY_LEAVES':'#eda6bd','OAK_LEAVES':'#4c8544','CHERRY_LOG':'#67434c','OAK_LOG':'#79583d','PINK_TULIP':'#ed84ab','WHITE_TULIP':'#fff3e1','STONE_BRICK_WALL':'#a3aca1','DARK_OAK_FENCE':'#65513a','LANTERN':'#ffd480','SPRUCE_SLAB':'#96714e','SPRUCE_PLANKS':'#8c6647','POTTED_FLOWERING_AZALEA_BUSH':'#d286af','STRIPPED_SPRUCE_LOG':'#95744d','DARK_OAK_PLANKS':'#5b4534','WAXED_CUT_COPPER':'#c07a53','WAXED_CUT_COPPER_SLAB':'#ce9065','LIGHTNING_ROD':'#b87149','IRON_CHAIN':'#505c62','STONE_BRICK_SLAB':'#a5afa4'}
def rgb(color):return tuple(int(color[i:i+2],16)for i in(1,3,5))
def tone(color,factor):return tuple(min(255,max(0,round(n*factor)))for n in rgb(color))
def project(x,y,z):return (750+(x-z)*5.8,600+(x+z)*2.9-y*6.6)
# Soft shadow anchors the floating stone foundation.
for i in range(16,0,-1):
 pad=i*3;draw.ellipse((240-pad,785-pad/2,1260+pad,944+pad/2),fill=(242-i,246-i,245-i))
for (x,y,z),kind in sorted(blocks.items(),key=lambda item:(item[0][0]+item[0][2],item[0][1],item[0][0])):
 color=colors.get(kind,'#a7ab9d');noise=((x*17+z*23+y*13)%9-4)*.012
 def point(dx,dy,dz):return project(x+dx,y+dy,z+dz)
 if(x+1,y,z)not in blocks:draw.polygon([point(1,0,0),point(1,0,1),point(1,1,1),point(1,1,0)],fill=tone(color,.68+noise))
 if(x,y,z+1)not in blocks:draw.polygon([point(0,0,1),point(1,0,1),point(1,1,1),point(0,1,1)],fill=tone(color,.84+noise))
 if(x,y+1,z)not in blocks:
  draw.polygon([point(0,1,0),point(1,1,0),point(1,1,1),point(0,1,1)],fill=tone(color,1+noise))
  if kind in {'STONE_BRICKS','QUARTZ_BRICKS','WAXED_CUT_COPPER'}:draw.line([point(0,1,0),point(1,1,0),point(1,1,1)],fill=tone(color,.85),width=1)
# Clearly label this as a block-plan render rather than a claimed client screenshot.
draw.line((76,989,1424,989),fill='#e2e9e8',width=2)
draw.text((76,1011),'BLOCK PLAN PREVIEW',font=B(18),fill='#607d7f')
draw.text((76,1042),'Existing terrain preserved  |  Built once, retained after restart',font=F(17),fill='#73888c')
draw.text((1133,1014),'Paper 26.2',font=B(18),fill='#347b83');draw.text((1133,1042),'Java 25',font=F(17),fill='#73888c')
out=Path(__file__).resolve().parent.parent/'Spawn-Garden-100x100.png';img.save(out,optimize=True)
print(out,len(rows),out.stat().st_size)
