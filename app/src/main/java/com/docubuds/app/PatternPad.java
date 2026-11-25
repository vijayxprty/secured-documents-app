package com.docubuds.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.ArrayList;

public final class PatternPad extends View {
  private final Paint paint = new Paint(3);
  private int colour = Color.rgb(0, 41, 112);
  private final ArrayList<Integer> chosen = new ArrayList<>();
  private float fingerX, fingerY;
  private boolean drawing;

  public PatternPad(Context c) {
    super(c);
    setContentDescription(
        "Draw a pattern using at least six dots. App PIN is also available for accessible text"
            + " entry.");
  }

  public PatternPad(Context c, int colour) {
    this(c);
    this.colour = colour;
  }

  private float x(int i) {
    return getWidth() * (.2f + (i % 3) * .3f);
  }

  private float y(int i) {
    return getHeight() * (.2f + (i / 3) * .3f);
  }

  public char[] value() {
    StringBuilder b = new StringBuilder();
    for (int i : chosen) b.append(i);
    return b.toString().toCharArray();
  }

  public void clear() {
    chosen.clear();
    invalidate();
  }

  protected void onDraw(Canvas c) {
    paint.setColor(colour);
    paint.setStrokeWidth(8);
    paint.setStrokeCap(Paint.Cap.ROUND);
    for (int i = 1; i < chosen.size(); i++)
      c.drawLine(
          x(chosen.get(i - 1)), y(chosen.get(i - 1)), x(chosen.get(i)), y(chosen.get(i)), paint);
    if (drawing && !chosen.isEmpty())
      c.drawLine(
          x(chosen.get(chosen.size() - 1)),
          y(chosen.get(chosen.size() - 1)),
          fingerX,
          fingerY,
          paint);
    for (int i = 0; i < 9; i++) {
      paint.setColor(chosen.contains(i) ? colour : Color.rgb(167, 188, 207));
      c.drawCircle(x(i), y(i), chosen.contains(i) ? 18 : 12, paint);
    }
  }

  public boolean onTouchEvent(MotionEvent e) {
    fingerX = e.getX();
    fingerY = e.getY();
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      chosen.clear();
      drawing = true;
    }
    if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
      for (int n = 0; n < 9; n++)
        if (Math.hypot(x(n) - fingerX, y(n) - fingerY) < getWidth() * .11 && !chosen.contains(n)) {
          if (!chosen.isEmpty()) {
            int last = chosen.get(chosen.size() - 1), dx = n % 3 - last % 3, dy = n / 3 - last / 3;
            if ((Math.abs(dx) == 2 && dy == 0)
                || (Math.abs(dy) == 2 && dx == 0)
                || (Math.abs(dx) == 2 && Math.abs(dy) == 2)) {
              int middle = (last + n) / 2;
              if (!chosen.contains(middle)) chosen.add(middle);
            }
          }
          chosen.add(n);
          break;
        }
    }
    if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
      drawing = false;
    invalidate();
    return true;
  }
}
