package com.docubuds.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class Icons extends View {
  private final String name;
  private final int colour;
  private final Paint p = new Paint(3);

  public Icons(Context c, String n, int colour) {
    super(c);
    name = n;
    this.colour = colour;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas cc) {
    super.onDraw(cc);
    cc.save();
    cc.translate(getWidth() / 2f, getHeight() / 2f);
    float s = Math.min(getWidth(), getHeight()) / 28f;
    cc.scale(s, s);
    cc.translate(-12, -12);
    p.setColor(colour);
    p.setStrokeWidth(1.7f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeCap(Paint.Cap.ROUND);
    switch (name) {
      case "plus":
        line(cc, 12, 4, 12, 20);
        line(cc, 4, 12, 20, 12);
        break;
      case "scan":
        line(cc, 2, 8, 2, 2);
        line(cc, 2, 2, 8, 2);
        line(cc, 16, 2, 22, 2);
        line(cc, 22, 2, 22, 8);
        line(cc, 2, 16, 2, 22);
        line(cc, 2, 22, 8, 22);
        line(cc, 16, 22, 22, 22);
        line(cc, 22, 22, 22, 16);
        line(cc, 6, 12, 18, 12);
        break;
      case "note":
        cc.drawRoundRect(3, 2, 21, 22, 3, 3, p);
        line(cc, 7, 7, 16, 7);
        line(cc, 7, 11, 16, 11);
        line(cc, 7, 16, 12, 16);
        line(cc, 15, 18, 20, 13);
        break;
      case "search":
        cc.drawCircle(10, 10, 6.5f, p);
        line(cc, 15, 15, 21, 21);
        break;
      case "back":
        line(cc, 15, 5, 8, 12);
        line(cc, 8, 12, 15, 19);
        break;
      case "next":
        line(cc, 9, 5, 16, 12);
        line(cc, 16, 12, 9, 19);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) cc.drawCircle(12, 5 + i * 7, 1.4f, p);
        break;
      case "lock":
        cc.drawRoundRect(4, 10, 20, 22, 3, 3, p);
        cc.drawArc(7, 1, 17, 16, 180, 180, false, p);
        line(cc, 12, 15, 12, 17);
        break;
      case "heart":
        Path h = new Path();
        h.moveTo(12, 21);
        h.cubicTo(-8, 9, 5, -4, 12, 5);
        h.cubicTo(19, -4, 32, 9, 12, 21);
        cc.drawPath(h, p);
        break;
      case "folder":
        Path f = new Path();
        f.moveTo(2, 7);
        f.lineTo(2, 4);
        f.lineTo(9, 4);
        f.lineTo(12, 7);
        f.lineTo(22, 7);
        f.lineTo(22, 21);
        f.lineTo(2, 21);
        f.close();
        cc.drawPath(f, p);
        break;
      case "bank":
        Path b = new Path();
        b.moveTo(2, 7);
        b.lineTo(12, 1);
        b.lineTo(22, 7);
        b.close();
        cc.drawPath(b, p);
        for (int x : new int[] {5, 12, 19}) line(cc, x, 10, x, 19);
        line(cc, 2, 22, 22, 22);
        break;
      case "home":
        Path house = new Path();
        house.moveTo(1, 11);
        house.lineTo(12, 2);
        house.lineTo(23, 11);
        cc.drawPath(house, p);
        line(cc, 4, 10, 4, 22);
        line(cc, 4, 22, 20, 22);
        line(cc, 20, 22, 20, 10);
        cc.drawRect(10, 15, 14, 22, p);
        break;
      case "work":
        cc.drawRoundRect(2, 7, 22, 21, 3, 3, p);
        cc.drawRoundRect(8, 2, 16, 7, 2, 2, p);
        line(cc, 2, 12, 22, 12);
        break;
      case "id":
        cc.drawRoundRect(1, 4, 23, 20, 3, 3, p);
        cc.drawCircle(7, 10, 2, p);
        cc.drawArc(3, 13, 11, 19, 180, 180, false, p);
        line(cc, 14, 9, 19, 9);
        line(cc, 14, 14, 19, 14);
        break;
      case "bell":
        cc.drawArc(5, 3, 19, 18, 180, 180, false, p);
        line(cc, 5, 10, 4, 18);
        line(cc, 19, 10, 20, 18);
        line(cc, 4, 18, 20, 18);
        cc.drawArc(9, 18, 15, 24, 0, 180, false, p);
        break;
      case "settings":
        cc.drawCircle(12, 12, 4, p);
        cc.drawCircle(12, 12, 9, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(
              cc,
              (float) (12 + 9 * Math.cos(a)),
              (float) (12 + 9 * Math.sin(a)),
              (float) (12 + 12 * Math.cos(a)),
              (float) (12 + 12 * Math.sin(a)));
        }
        break;
      case "star":
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
          double a = i * Math.PI / 5 - Math.PI / 2;
          float r = i % 2 == 0 ? 11 : 5;
          float x = (float) (12 + r * Math.cos(a)), y = (float) (12 + r * Math.sin(a));
          if (i == 0) star.moveTo(x, y);
          else star.lineTo(x, y);
        }
        star.close();
        cc.drawPath(star, p);
        break;
      case "car":
        cc.drawRoundRect(2, 10, 22, 20, 3, 3, p);
        line(cc, 5, 10, 8, 3);
        line(cc, 8, 3, 17, 3);
        line(cc, 17, 3, 20, 10);
        cc.drawCircle(6, 15, 1, p);
        cc.drawCircle(18, 15, 1, p);
        break;
      default:
        Path d = new Path();
        d.moveTo(5, 1);
        d.lineTo(15, 1);
        d.lineTo(21, 7);
        d.lineTo(21, 23);
        d.lineTo(5, 23);
        d.close();
        cc.drawPath(d, p);
        line(cc, 15, 1, 15, 7);
        line(cc, 15, 7, 21, 7);
        line(cc, 9, 12, 17, 12);
        line(cc, 9, 16, 17, 16);
    }
    cc.restore();
  }

  private void line(Canvas c, float a, float b, float x, float y) {
    c.drawLine(a, b, x, y, p);
  }
}
