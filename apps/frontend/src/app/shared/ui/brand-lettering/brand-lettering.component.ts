import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  ENCORE_WORDMARK_GLYPHS,
  BrandLetteringGlyphMap,
  BrandLetteringGlyphRecord,
} from './brand-lettering-glyphs';

interface BrandLetteringGlyphInstance {
  key: string;
  char: string;
  x: number;
  scale: number;
  width: number;
  transform: string;
  clipId: string;
  data: BrandLetteringGlyphRecord;
}

interface BrandLetteringLineView {
  key: string;
  width: number;
  height: number;
  mainGradientId: string;
  bevelGradientId: string;
  edgeGradientId: string;
  glyphs: BrandLetteringGlyphInstance[];
}

let BrandLetteringInstance = 0;

@Component({
  selector: 'rr-brand-lettering',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './brand-lettering.component.html',
  styleUrls: ['./brand-lettering.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'brand-lettering-host',
    '[attr.aria-label]': 'text()',
    role: 'img',
  },
})
export class BrandLetteringComponent {
  readonly text = input('');
  readonly height = input(118);
  readonly outline = input(false);
  readonly letterSpacing = input(-8);
  readonly lineGap = input(12);
  readonly spaceWidth = input(22);
  readonly glyphs: BrandLetteringGlyphMap = ENCORE_WORDMARK_GLYPHS;

  private readonly instanceId = `brand-lettering-${++BrandLetteringInstance}`;

  readonly lines = computed<BrandLetteringLineView[]>(() => {
    const content = this.text() ?? '';
    const targetHeight = Math.max(1, Number(this.height()) || 118);
    const letterSpacing = Number(this.letterSpacing()) || 0;
    const spaceWidth = Math.max(0, Number(this.spaceWidth()) || 0);
    const rawLines = content.split(/\r?\n/);

    return rawLines.map((lineText, lineIndex) => {
      const glyphs: BrandLetteringGlyphInstance[] = [];
      let cursorX = 0;
      let endedOnGlyph = false;

      [...lineText].forEach((char, glyphIndex) => {
        if (char === ' ') {
          cursorX += spaceWidth;
          endedOnGlyph = false;
          return;
        }

        if (char === '	') {
          cursorX += spaceWidth * 4;
          endedOnGlyph = false;
          return;
        }

        const data = this.glyphs[char];
        if (!data) {
          cursorX += spaceWidth;
          endedOnGlyph = false;
          return;
        }

        const scale = targetHeight / data.height;
        const width = data.width * scale;
        const key = `${this.instanceId}-l${lineIndex}-g${glyphIndex}`;
        glyphs.push({
          key,
          char,
          x: cursorX,
          scale,
          width,
          transform: `translate(${cursorX} 0) scale(${scale})`,
          clipId: `${key}-clip`,
          data,
        });
        cursorX += width + letterSpacing;
        endedOnGlyph = true;
      });

      if (endedOnGlyph) {
        cursorX -= letterSpacing;
      }

      const width = Math.max(1, cursorX || 1);
      return {
        key: `${this.instanceId}-line-${lineIndex}`,
        width,
        height: targetHeight,
        mainGradientId: `${this.instanceId}-main-${lineIndex}`,
        bevelGradientId: `${this.instanceId}-bevel-${lineIndex}`,
        edgeGradientId: `${this.instanceId}-edge-${lineIndex}`,
        glyphs,
      };
    });
  });

  trackLine = (_: number, line: BrandLetteringLineView) => line.key;
  trackGlyph = (_: number, glyph: BrandLetteringGlyphInstance) => glyph.key;
}
