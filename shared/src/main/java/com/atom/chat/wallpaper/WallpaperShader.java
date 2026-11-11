package com.atom.chat.wallpaper;

/** Static compositions with analytic surfaces; edge AA is one logical raster pixel. */
final class WallpaperShader {
    private WallpaperShader() { }
    static final String SOURCE = """
        uniform float2 size;
        uniform float mode;
        float aa(float d,float px) { return smoothstep(-px,px,d); }
        float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
        half4 main(float2 xy) {
            float2 uv=xy/size,p=(xy-.5*size)/min(size.x,size.y);
            float px=1./min(size.x,size.y);
            float3 c;
            if(mode<.5) {
                c=float3(.015,.035,.095);
                // Broad cobalt sheets with clear silhouettes, restrained cold edge light.
                for(int i=0;i<4;i++) {
                    float fi=float(i);
                    float center=-.90+fi*.43+.24*sin(p.y*1.5+fi*.4)-.16*p.y;
                    float width=.33,t=(p.x-center)/width,d=width-abs(p.x-center);
                    float face=pow(clamp(1.-t*t,0.,1.),.7);
                    float3 sheet=mix(float3(.025,.075,.22),float3(.12,.31,.68),face);
                    sheet*=.78+.22*cos(p.y*.8+fi*.5);
                    sheet+=float3(.10,.22,.35)*exp(-pow((t+.76)/.045,2.));
                    c*=1.-.35*exp(-max(0.,-d)/.022)*(1.-aa(d,px));
                    c=mix(c,sheet,aa(d,px));
                }
            } else if(mode<1.5) {
                c=float3(.83,.74,.60);
                float d=p.y+.7*p.x+.40;
                c=mix(c,float3(.57,.29,.16),aa(d,px));
                d=p.y-.38*p.x-.48;
                c=mix(c,float3(.29,.22,.17),aa(d,px));
                d=.43-length(p-float2(.42,-.85));
                c=mix(c,float3(.94,.86,.70),aa(d,px));
                // A quiet inset plane creates scale without fine line noise.
                d=min(.19-abs(p.x+.34),.42-abs(p.y+.46));
                c=mix(c,float3(.72,.47,.28),aa(d,px));
            } else if(mode<2.5) {
                c=float3(.018,.065,.048);
                // Overlapping continuous emerald folds, shaped as reflective surfaces.
                for(int i=0;i<5;i++) {
                    float fi=float(i);
                    float bend=.30*sin(p.y*1.65+fi*.18)+.13*sin(p.y*3.25+fi*.35);
                    float center=-.85+fi*.38+bend;
                    float width=.27+.035*cos(p.y*2.+fi);
                    float t=(p.x-center)/width;
                    float d=width-abs(p.x-center);
                    float face=pow(clamp(1.-t*t,0.,1.),.65);
                    float3 sheet=mix(float3(.015,.105,.064),float3(.12,.40,.24),face);
                    sheet*=.80+.20*cos(p.y*.9+fi*.6);
                    float glint=exp(-pow((t+.78)/.035,2.));
                    sheet+=float3(.40,.33,.115)*glint*(.28+.30*cos(p.y*1.4+fi));
                    // Tight contact shadow belongs to the overlap, not the whole image.
                    c*=1.-.30*exp(-max(0.,-d)/.018)*(1.-aa(d,px));
                    c=mix(c,sheet,aa(d,px));
                }
            } else if(mode<3.5) {
                c=float3(.045,.018,.035);
                for(int i=0;i<3;i++) {
                    float fi=float(i);
                    float center=-.39+fi*.37+.34*sin(p.y*1.65+fi*.32);
                    float width=.115+.018*cos(p.y*1.6+fi);
                    float t=(p.x-center)/width, d=width-abs(p.x-center);
                    float3 hue=mix(float3(.47,.055,.23),float3(.72,.35,.065),fi*.5);
                    float face=.40+.60*pow(clamp(1.-t*t,0.,1.),.65);
                    float3 ribbon=hue*face;
                    ribbon+=mix(float3(.35,.12,.25),float3(.38,.25,.10),fi*.5)
                            *exp(-pow((t+.55)/.065,2.));
                    c+=hue*.045*exp(-max(0.,-d)/.018)*(1.-aa(d,px));
                    c=mix(c,ribbon,aa(d,px));
                }
            } else if(mode<4.5) {
                c=float3(.105,.060,.035);
                for(int i=0;i<6;i++) {
                    float fi=float(i),d=p.y+.25*cos(p.x*2.4+fi*.45)-fi*.34+.80;
                    float cover=aa(d,px);
                    float3 layer=mix(float3(.27,.13,.065),float3(.71,.48,.24),fi/6.);
                    layer*=.75+.25*smoothstep(-.5,.5,p.x);
                    c*=1.-.42*exp(-abs(d+.014)/.022);
                    c=mix(c,layer,cover);
                    c+=float3(.10,.065,.027)*exp(-abs(d)/(px*1.3));
                }
            } else if(mode<5.5) {
                c=mix(float3(.94,.91,.82),float3(.84,.83,.70),uv.y);
                float d=p.y+.5*p.x-.28;
                c=mix(c,float3(.51,.55,.35),aa(d,px));
                d=.50-length(p-float2(.30,-.83));
                c=mix(c,float3(.76,.76,.57),aa(d,px));
                d=min(.23-abs(p.x+.39),.39-abs(p.y+.37));
                c=mix(c,float3(.95,.92,.83),aa(d,px));
            } else {
                c=float3(.24,.23,.28);
                for(int i=0;i<5;i++) {
                    float fi=float(i),center=-1.+fi*.48+.22*sin(p.y*1.6+fi*.5)-.20*p.y;
                    float width=.34,t=(p.x-center)/width,d=width-abs(p.x-center);
                    float3 base=mix(float3(.43,.37,.46),float3(.48,.56,.44),fi*.25);
                    float face=.43+.57*pow(clamp(1.-t*t,0.,1.),.5);
                    float3 silk=base*face;
                    silk+=float3(.14,.13,.13)*exp(-pow((t+.72)/.055,2.));
                    c*=1.-.27*exp(-max(0.,-d)/.018)*(1.-aa(d,px));
                    c=mix(c,silk,aa(d,px));
                }
            }
            // Sub-quantization dither prevents banding, never substitutes for structure.
            return half4(clamp(c+(hash(xy)-.5)/510.,0.,1.),1.);
        }
        """;
}
