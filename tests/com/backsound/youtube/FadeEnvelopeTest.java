package com.backsound.youtube;
public final class FadeEnvelopeTest {
 static int checks;
 static void eq(double actual,double expected) { checks++; if (Math.abs(actual-expected)>1e-8) throw new AssertionError(actual+" != "+expected); }
 static void ok(boolean test) {checks++; if(!test)throw new AssertionError();}
 public static void main(String[] args) {
  FadeEnvelope fade=new FadeEnvelope();
  fade.set(1);fade.to(0,2_000_000_000L,0);
  eq(fade.value(0),1);eq(fade.value(1_000_000_000L),.5);eq(fade.value(2_000_000_000L),0);
  ok(!fade.done(1_999_999_999L));ok(fade.done(2_000_000_000L));
  double previous=1;
  for(int i=0;i<=40;i++) {double gain=fade.value(i*50_000_000L);ok(gain<=previous && gain>=0);previous=gain;}
  // Border reversal preserves current gain, without a volume jump.
  fade.set(1);fade.to(0,2_000_000_000L,0);double atBorder=fade.value(700_000_000L);
  fade.to(1,1_500_000_000L,700_000_000L);eq(fade.value(700_000_000L),atBorder);
  ok(fade.value(1_000_000_000L)>atBorder);eq(fade.value(2_200_000_000L),1);
  // Tick lag advances time, not a count of packets or repeated audio starts.
  fade.set(1);fade.to(0,2_000_000_000L,0);eq(fade.value(9_000_000_000L),0);ok(fade.done(9_000_000_000L));
  fade.set(0);fade.to(1,0,3);eq(fade.value(3),1);ok(fade.done(3));
  eq(FadeEnvelope.distance(1),0);eq(FadeEnvelope.distance(.5),8);eq(FadeEnvelope.distance(0),16);
  eq(FadeEnvelope.distance(-2),16);eq(FadeEnvelope.distance(5),0);
  System.out.println("PASS: "+checks+" fade timing, monotonic gain, reversal, lag and attenuation checks");
 }
}
