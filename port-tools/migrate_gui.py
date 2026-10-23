from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
GUI=ROOT/'src/main/java/moze_intel/projecte/gameObjs/gui'
for p in GUI.glob('*.java'):
 s=p.read_text(encoding='utf-8')
 # Constructor dimensions are immutable in vanilla now.
 pattern=r'super\(([^;]+)\);((?:(?!super\().)*?)\s*this.imageWidth = ([^;]+);\s*this.imageHeight = ([^;]+);'
 s=re.sub(pattern,lambda m:'super('+m[1]+', '+m[3]+', '+m[4]+');'+m[2],s,flags=re.S)
 if p.name=='AbstractCollectorScreen.java':
  s=s.replace('super(container, invPlayer, title);','this(container, invPlayer, title, 176, 166);',1)
  idx=s.index('\n\tprotected abstract')
  s=s[:idx]+'''
    protected AbstractCollectorScreen(T container, Inventory invPlayer, Component title, int width, int height) {
        super(container, invPlayer, title, width, height);
    }
'''+s[idx:]
 if p.name=='GUIRelay.java':
  s=s.replace('int emcBarShift, int shiftX, int shiftY)', 'int emcBarShift, int shiftX, int shiftY, int width, int height)').replace('super(container, invPlayer, title);','super(container, invPlayer, title, width, height);')
 if p.name=='PEContainerScreen.java':
  s=s.replace('\t@Override\n\tpublic void render(', '''    public PEContainerScreen(T container, Inventory invPlayer, Component title, int width, int height) {
        super(container, invPlayer, title, width, height);
    }

    protected abstract void renderBg(GuiGraphicsExtractor graphics, float partialTicks, int mouseX, int mouseY);

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        renderBg(graphics, partialTicks, mouseX, mouseY);
        super.extractContents(graphics, mouseX, mouseY, partialTicks);
    }

\t@Override
\tpublic void extractRenderState(''').replace('super.render(', 'super.extractRenderState(')
 s=s.replace('renderLabels(', 'extractLabels(').replace('renderTooltip(', 'extractTooltip(').replace('.drawString(', '.text(').replace('.renderItem(', '.item(').replace('.renderItemDecorations(', '.itemDecorations(')
 s=s.replace('textBoxFilter.render(', 'textBoxFilter.extractRenderState(')
 s=re.sub(r'^\s*RenderSystem\.(setShader|setShaderColor|setShaderTexture)\([^\n]+\);\n','\n',s,flags=re.M)
 # Insert the explicit GUI render pipeline and texture sizes in old blits.
 s=re.sub(r'graphics.blit\(([^;]+)\);',lambda m:'graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, '+m[1]+', 256, 256);',s)
 s=s.replace('graphics.blitSprite(', 'graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ')
 s=re.sub(r'0x([0-9A-Fa-f]{6})\b',r'0xFF\1',s)
 s=s.replace('resize(@NotNull Minecraft minecraft, int width, int height)', 'resize(int width, int height)').replace('init(minecraft, width, height)', 'super.resize(width, height)')
 s=s.replace('keyPressed(int keyCode, int scanCode, int modifiers) {', 'keyPressed(net.minecraft.client.input.KeyEvent event) {\n\t\tint keyCode = event.key();')
 s=s.replace('keyPressed(keyCode, scanCode, modifiers)', 'keyPressed(event)')
 s=s.replace('mouseClicked(double x, double y, int mouseButton) {', 'mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {\n\t\tdouble x = event.x(), y = event.y();\n\t\tint mouseButton = event.button();')
 s=s.replace('super.mouseClicked(x, y, mouseButton)', 'super.mouseClicked(event, doubleClick)')
 s=re.sub(r'setTooltipForNextRenderPass\((.*)\);',r'graphics.setTooltipForNextFrame(\1, mouseX, mouseY);',s)
 p.write_text(s,encoding='utf-8')
