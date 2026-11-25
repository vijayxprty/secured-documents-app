package com.docubuds.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector illustration, rendered locally at any density. */
public final class VaultArt extends View {
  private final Paint p = new Paint(3);
  private final boolean locked;

  public VaultArt(Context c, boolean locked) {
    super(c);
    this.locked = locked;
    setContentDescription(
        locked ? "A personal vault with a lock" : "Documents gathered in a personal folder");
  }

  protected void onDraw(Canvas canvas) {
    float scale = Math.min(getWidth() / 320f, getHeight() / 210f);
    canvas.save();
    canvas.translate((getWidth() - 320 * scale) / 2, (getHeight() - 210 * scale) / 2);
    canvas.scale(scale, scale);
    p.setColor(Color.rgb(231, 247, 254));
    canvas.drawCircle(160, 103, 91, p);
    p.setColor(Color.rgb(211, 233, 245));
    canvas.drawOval(68, 180, 260, 197, p);
    canvas.save();
    canvas.rotate(-12, 155, 110);
    p.setColor(Color.rgb(154, 215, 239));
    canvas.drawRoundRect(85, 25, 220, 168, 16, 16, p);
    canvas.restore();
    canvas.save();
    canvas.rotate(8, 170, 100);
    p.setColor(Color.WHITE);
    canvas.drawRoundRect(108, 18, 238, 168, 14, 14, p);
    p.setColor(Color.rgb(192, 219, 235));
    for (int i = 0; i < 4; i++)
      canvas.drawRoundRect(128, 52 + i * 20, 208 - i % 2 * 25, 57 + i * 20, 3, 3, p);
    canvas.restore();
    p.setColor(Color.rgb(0, 41, 112));
    Path folder = new Path();
    folder.moveTo(66, 94);
    folder.quadTo(66, 81, 81, 81);
    folder.lineTo(125, 81);
    folder.lineTo(141, 98);
    folder.lineTo(248, 98);
    folder.quadTo(264, 98, 262, 116);
    folder.lineTo(250, 176);
    folder.quadTo(248, 188, 232, 188);
    folder.lineTo(81, 188);
    folder.quadTo(68, 188, 68, 173);
    folder.close();
    canvas.drawPath(folder, p);
    p.setColor(Color.rgb(0, 143, 199));
    canvas.drawRoundRect(67, 111, 258, 187, 14, 14, p);
    p.setColor(Color.WHITE);
    canvas.drawCircle(174, 146, 26, p);
    p.setColor(Color.rgb(0, 41, 112));
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(3);
    canvas.drawRoundRect(163, 143, 185, 160, 4, 4, p);
    canvas.drawArc(166, 132, 182, 151, 180, 180, false, p);
    p.setStyle(Paint.Style.FILL);
    canvas.drawCircle(174, 150, 2, p);
    p.setColor(Color.rgb(0, 186, 242));
    canvas.drawCircle(64, 45, 6, p);
    p.setColor(Color.rgb(84, 163, 205));
    canvas.drawCircle(264, 62, 4, p);
    p.setStrokeWidth(3);
    canvas.drawLine(265, 147, 275, 147, p);
    canvas.drawLine(270, 142, 270, 152, p);
    canvas.restore();
  }
}
