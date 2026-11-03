import com.atom.chat.render.ImageQuality;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.util.Arrays;
public class OversizeImageBudgetProbe {
  public static void main(String[] args) {
    try (Surface surface = Surface.makeRasterN32Premul(3076, 3076)) {
      surface.getCanvas().clear(0xff5183b5);
      try (Image source = surface.makeImageSnapshot()) {
        for (int i=0;i<3;i++) ImageQuality.scaledFor(source,1025);
        io.github.humbleui.skija.impl.Stats.enabled=true; long callsBefore=io.github.humbleui.skija.impl.Stats.nativeCalls; long[] times=new long[12];
        for (int i=0;i<times.length;i++) {
          long t=System.nanoTime(); Image result=ImageQuality.scaledFor(source,1025); times[i]=System.nanoTime()-t;
          if(result!=source || result.isClosed()) throw new AssertionError("invalid fallback");
        }
        long calls=io.github.humbleui.skija.impl.Stats.nativeCalls-callsBefore; System.out.println("native_calls_total="+calls); Arrays.sort(times);
        System.out.printf("source=3076x3076 target=1025 cacheBudget=4194304 samples=%d p50_ms=%.3f min_ms=%.3f max_ms=%.3f%n", times.length, (times[5]+times[6])/2e6,times[0]/1e6,times[11]/1e6);
      }
    } finally { ImageQuality.release(); }
  }
}
