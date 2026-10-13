/**
 * Живая краска в герое: стабильные жидкости (Stam) на WebGL — адвекция, завихрённость, давление
 * Якоби ×20, вычитание градиента. Краситель хранит поглощение (1 − цвет), поэтому смешение
 * субтрактивное, как у настоящей краски: синий + жёлтый темнеют, а не светлеют до белого.
 * WebGL2 с half-float (RG16F/R16F, с откатом на RGBA), иначе WebGL1 + OES_texture_half_float;
 * без линейной фильтрации — ручная билинейная в адвекции. Нет поддержки — возвращает null,
 * герой показывает статичный градиент.
 */

export interface Fluid {
  canvas: HTMLCanvasElement;
  splat: (x: number, y: number, dx: number, dy: number, color: [number, number, number]) => void;
  step: (dt: number) => void;
  render: () => void;
  randomSplats: (n: number) => void;
  pigment: (hex: string, k?: number) => [number, number, number];
  nextColor: () => string;
  force: number;
  resize: () => void;
  dispose: () => void;
}

interface Fmt { internal: number; format: number }
interface Target { tex: WebGLTexture; fbo: WebGLFramebuffer; w: number; h: number; tx: number; ty: number; attach: (id: number) => number }
interface Double { w: number; h: number; tx: number; ty: number; readonly read: Target; readonly write: Target; swap: () => void }
interface Program { u: Record<string, WebGLUniformLocation | null>; bind: () => void }

const VS = `precision highp float;attribute vec2 aPosition;varying vec2 vUv,vL,vR,vT,vB;uniform vec2 texelSize;
void main(){vUv=aPosition*.5+.5;vL=vUv-vec2(texelSize.x,0.);vR=vUv+vec2(texelSize.x,0.);vT=vUv+vec2(0.,texelSize.y);vB=vUv-vec2(0.,texelSize.y);gl_Position=vec4(aPosition,0.,1.);}`;

const FS = {
  clear: `precision mediump float;varying highp vec2 vUv;uniform sampler2D uTexture;uniform float value;void main(){gl_FragColor=value*texture2D(uTexture,vUv);}`,
  // бумага × exp(−краситель) + блик по нормали из градиента толщины слоя
  display: `precision highp float;varying vec2 vUv,vL,vR,vT,vB;uniform sampler2D uTexture;uniform vec3 uBack;
void main(){vec3 d=min(texture2D(uTexture,vUv).rgb,vec3(4.));vec3 col=uBack*exp(-d*1.7);
float l=length(texture2D(uTexture,vL).rgb),r=length(texture2D(uTexture,vR).rgb),t=length(texture2D(uTexture,vT).rgb),b=length(texture2D(uTexture,vB).rgb);
vec3 n=normalize(vec3(l-r,b-t,.3));float sp=pow(max(dot(n,normalize(vec3(-.45,.55,1.))),0.),26.);col+=sp*.42*clamp(length(d)*.7,0.,1.);gl_FragColor=vec4(col,1.);}`,
  splat: `precision highp float;varying vec2 vUv;uniform sampler2D uTarget;uniform float aspectRatio;uniform vec3 color;uniform vec2 point;uniform float radius;
void main(){vec2 p=vUv-point;p.x*=aspectRatio;vec3 s=exp(-dot(p,p)/radius)*color;gl_FragColor=vec4(texture2D(uTarget,vUv).xyz+s,1.);}`,
  advection: `precision highp float;varying vec2 vUv;uniform sampler2D uVelocity,uSource;uniform vec2 texelSize,dyeTexelSize;uniform float dt,dissipation;
vec4 bilerp(sampler2D s,vec2 uv,vec2 ts){vec2 st=uv/ts-.5;vec2 i=floor(st),f=fract(st);vec4 a=texture2D(s,(i+vec2(.5,.5))*ts),b=texture2D(s,(i+vec2(1.5,.5))*ts),c=texture2D(s,(i+vec2(.5,1.5))*ts),d=texture2D(s,(i+vec2(1.5,1.5))*ts);return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}
void main(){
#ifdef MANUAL_FILTERING
vec2 coord=vUv-dt*bilerp(uVelocity,vUv,texelSize).xy*texelSize;vec4 res=bilerp(uSource,coord,dyeTexelSize);
#else
vec2 coord=vUv-dt*texture2D(uVelocity,vUv).xy*texelSize;vec4 res=texture2D(uSource,coord);
#endif
gl_FragColor=res/(1.+dissipation*dt);}`,
  divergence: `precision mediump float;varying highp vec2 vUv,vL,vR,vT,vB;uniform sampler2D uVelocity;
void main(){float L=texture2D(uVelocity,vL).x,R=texture2D(uVelocity,vR).x,T=texture2D(uVelocity,vT).y,B=texture2D(uVelocity,vB).y;vec2 C=texture2D(uVelocity,vUv).xy;
if(vL.x<0.)L=-C.x;if(vR.x>1.)R=-C.x;if(vT.y>1.)T=-C.y;if(vB.y<0.)B=-C.y;gl_FragColor=vec4(.5*(R-L+T-B),0.,0.,1.);}`,
  curl: `precision mediump float;varying highp vec2 vUv,vL,vR,vT,vB;uniform sampler2D uVelocity;
void main(){float L=texture2D(uVelocity,vL).y,R=texture2D(uVelocity,vR).y,T=texture2D(uVelocity,vT).x,B=texture2D(uVelocity,vB).x;gl_FragColor=vec4(.5*(R-L-T+B),0.,0.,1.);}`,
  vorticity: `precision highp float;varying vec2 vUv,vL,vR,vT,vB;uniform sampler2D uVelocity,uCurl;uniform float curl,dt;
void main(){float L=texture2D(uCurl,vL).x,R=texture2D(uCurl,vR).x,T=texture2D(uCurl,vT).x,B=texture2D(uCurl,vB).x,C=texture2D(uCurl,vUv).x;
vec2 f=.5*vec2(abs(T)-abs(B),abs(R)-abs(L));f/=length(f)+.0001;f*=curl*C;f.y*=-1.;vec2 v=texture2D(uVelocity,vUv).xy+f*dt;v=min(max(v,-1000.),1000.);gl_FragColor=vec4(v,0.,1.);}`,
  pressure: `precision mediump float;varying highp vec2 vUv,vL,vR,vT,vB;uniform sampler2D uPressure,uDivergence;
void main(){float L=texture2D(uPressure,vL).x,R=texture2D(uPressure,vR).x,T=texture2D(uPressure,vT).x,B=texture2D(uPressure,vB).x,d=texture2D(uDivergence,vUv).x;gl_FragColor=vec4((L+R+B+T-d)*.25,0.,0.,1.);}`,
  gradient: `precision mediump float;varying highp vec2 vUv,vL,vR,vT,vB;uniform sampler2D uPressure,uVelocity;
void main(){float L=texture2D(uPressure,vL).x,R=texture2D(uPressure,vR).x,T=texture2D(uPressure,vT).x,B=texture2D(uPressure,vB).x;vec2 v=texture2D(uVelocity,vUv).xy-vec2(R-L,T-B);gl_FragColor=vec4(v,0.,1.);}`,
} as const;

export function createFluid(canvas: HTMLCanvasElement, palette: readonly string[]): Fluid | null {
  const opts: WebGLContextAttributes = { alpha: false, depth: false, stencil: false, antialias: false, preserveDrawingBuffer: false };
  const gl2 = canvas.getContext("webgl2", opts);
  const gl = (gl2 ?? canvas.getContext("webgl", opts)) as WebGLRenderingContext | null;
  if (!gl) return null;
  // Программный WebGL (нет GPU или он в чёрном списке): симуляция там идёт рывками и съедает
  // процессор — лучше статичная подложка героя.
  const dbg = gl.getExtension("WEBGL_debug_renderer_info");
  const renderer = String(gl.getParameter(dbg ? dbg.UNMASKED_RENDERER_WEBGL : gl.RENDERER) ?? "");
  if (/swiftshader|llvmpipe|softpipe|software/i.test(renderer)) return null;

  let halfType: number | undefined;
  let linear: unknown;
  if (gl2) {
    gl2.getExtension("EXT_color_buffer_float");
    linear = gl2.getExtension("OES_texture_float_linear");
    halfType = gl2.HALF_FLOAT;
  } else {
    const hf = gl.getExtension("OES_texture_half_float");
    linear = gl.getExtension("OES_texture_half_float_linear");
    halfType = hf?.HALF_FLOAT_OES;
  }
  if (!halfType) return null;
  const HT = halfType;

  function supportRT(internal: number, format: number): boolean {
    const t = gl!.createTexture();
    gl!.bindTexture(gl!.TEXTURE_2D, t);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_MIN_FILTER, gl!.NEAREST);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_MAG_FILTER, gl!.NEAREST);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_WRAP_S, gl!.CLAMP_TO_EDGE);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_WRAP_T, gl!.CLAMP_TO_EDGE);
    gl!.texImage2D(gl!.TEXTURE_2D, 0, internal, 4, 4, 0, format, HT, null);
    const f = gl!.createFramebuffer();
    gl!.bindFramebuffer(gl!.FRAMEBUFFER, f);
    gl!.framebufferTexture2D(gl!.FRAMEBUFFER, gl!.COLOR_ATTACHMENT0, gl!.TEXTURE_2D, t, 0);
    const ok = gl!.checkFramebufferStatus(gl!.FRAMEBUFFER) === gl!.FRAMEBUFFER_COMPLETE;
    gl!.deleteFramebuffer(f);
    gl!.deleteTexture(t);
    return ok;
  }
  function fmt(internal: number, format: number): Fmt | null {
    if (supportRT(internal, format)) return { internal, format };
    if (gl2 && internal === gl2.R16F) return fmt(gl2.RG16F, gl2.RG);
    if (gl2 && internal === gl2.RG16F) return fmt(gl2.RGBA16F, gl2.RGBA);
    return null;
  }
  const fRGBA = gl2 ? fmt(gl2.RGBA16F, gl2.RGBA) : fmt(gl.RGBA, gl.RGBA);
  const fRG = gl2 ? fmt(gl2.RG16F, gl2.RG) : fRGBA;
  const fR = gl2 ? fmt(gl2.R16F, gl2.RED) : fRGBA;
  if (!fRGBA || !fRG || !fR) return null;
  const filt = linear ? gl.LINEAR : gl.NEAREST;

  function compile(type: number, src: string): WebGLShader {
    const s = gl!.createShader(type);
    if (!s) throw new Error("shader");
    gl!.shaderSource(s, src);
    gl!.compileShader(s);
    if (!gl!.getShaderParameter(s, gl!.COMPILE_STATUS)) throw new Error(gl!.getShaderInfoLog(s) ?? "shader");
    return s;
  }

  type Name = keyof typeof FS;
  let P: Record<Name, Program>;
  try {
    const vs = compile(gl.VERTEX_SHADER, VS);
    const program = (name: string): Program => {
      const src = (name === "advection" && !linear ? "#define MANUAL_FILTERING\n" : "") + FS[name as Name];
      const p = gl.createProgram();
      if (!p) throw new Error("program");
      gl.attachShader(p, vs);
      gl.attachShader(p, compile(gl.FRAGMENT_SHADER, src));
      gl.bindAttribLocation(p, 0, "aPosition");
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p) ?? "link");
      const u: Record<string, WebGLUniformLocation | null> = {};
      const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS) as number;
      for (let i = 0; i < n; i++) {
        const info = gl.getActiveUniform(p, i);
        if (info) u[info.name] = gl.getUniformLocation(p, info.name);
      }
      return { u, bind: () => gl.useProgram(p) };
    };
    P = Object.fromEntries(Object.keys(FS).map((k) => [k, program(k)])) as Record<Name, Program>;
  } catch {
    return null;
  }

  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, -1, 1, 1, 1, 1, -1]), gl.STATIC_DRAW);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array([0, 1, 2, 0, 2, 3]), gl.STATIC_DRAW);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
  gl.enableVertexAttribArray(0);

  function blit(t: Target | null) {
    if (t) { gl!.viewport(0, 0, t.w, t.h); gl!.bindFramebuffer(gl!.FRAMEBUFFER, t.fbo); }
    else { gl!.viewport(0, 0, gl!.drawingBufferWidth, gl!.drawingBufferHeight); gl!.bindFramebuffer(gl!.FRAMEBUFFER, null); }
    gl!.drawElements(gl!.TRIANGLES, 6, gl!.UNSIGNED_SHORT, 0);
  }
  function target(w: number, h: number, f: Fmt, param: number): Target {
    gl!.activeTexture(gl!.TEXTURE0);
    const tex = gl!.createTexture()!;
    gl!.bindTexture(gl!.TEXTURE_2D, tex);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_MIN_FILTER, param);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_MAG_FILTER, param);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_WRAP_S, gl!.CLAMP_TO_EDGE);
    gl!.texParameteri(gl!.TEXTURE_2D, gl!.TEXTURE_WRAP_T, gl!.CLAMP_TO_EDGE);
    gl!.texImage2D(gl!.TEXTURE_2D, 0, f.internal, w, h, 0, f.format, HT, null);
    const fbo = gl!.createFramebuffer()!;
    gl!.bindFramebuffer(gl!.FRAMEBUFFER, fbo);
    gl!.framebufferTexture2D(gl!.FRAMEBUFFER, gl!.COLOR_ATTACHMENT0, gl!.TEXTURE_2D, tex, 0);
    gl!.viewport(0, 0, w, h);
    gl!.clearColor(0, 0, 0, 1);
    gl!.clear(gl!.COLOR_BUFFER_BIT);
    return {
      tex, fbo, w, h, tx: 1 / w, ty: 1 / h,
      attach(id: number) { gl!.activeTexture(gl!.TEXTURE0 + id); gl!.bindTexture(gl!.TEXTURE_2D, tex); return id; },
    };
  }
  function double(w: number, h: number, f: Fmt, param: number): Double {
    let a = target(w, h, f, param), b = target(w, h, f, param);
    return { w, h, tx: 1 / w, ty: 1 / h, get read() { return a; }, get write() { return b; }, swap() { const t = a; a = b; b = t; } };
  }
  function res(r: number) {
    let ar = gl!.drawingBufferWidth / gl!.drawingBufferHeight;
    if (ar < 1) ar = 1 / ar;
    const mn = Math.round(r), mx = Math.round(r * ar);
    return gl!.drawingBufferWidth > gl!.drawingBufferHeight ? { w: mx, h: mn } : { w: mn, h: mx };
  }

  const C = { SIM: 128, DYE: innerWidth < 700 ? 512 : 900, DYE_DISS: 0.22, VEL_DISS: 0.25, PRESS: 0.8, ITER: 20, CURL: 22, RADIUS: 0.24, FORCE: 5600 };
  let vel!: Double, dye!: Double, divg!: Target, curlT!: Target, pres!: Double;
  const all: Target[] = [];
  function freeTargets() {
    for (const t of all.splice(0)) { gl!.deleteTexture(t.tex); gl!.deleteFramebuffer(t.fbo); }
  }
  function init() {
    freeTargets();
    const s = res(C.SIM), d = res(C.DYE);
    vel = double(s.w, s.h, fRG!, filt);
    dye = double(d.w, d.h, fRGBA!, filt);
    divg = target(s.w, s.h, fR!, gl!.NEAREST);
    curlT = target(s.w, s.h, fR!, gl!.NEAREST);
    pres = double(s.w, s.h, fR!, gl!.NEAREST);
    all.push(vel.read, vel.write, dye.read, dye.write, divg, curlT, pres.read, pres.write);
  }
  function size(): boolean {
    const dpr = Math.min(1.5, devicePixelRatio || 1), r = canvas.getBoundingClientRect();
    const w = Math.max(2, Math.round(r.width * dpr)), h = Math.max(2, Math.round(r.height * dpr));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; return true; }
    return false;
  }
  size();
  init();

  const ar = () => canvas.width / canvas.height;
  const u = (name: Name, key: string) => P[name].u[key] ?? null;

  function splat(x: number, y: number, dx: number, dy: number, col: [number, number, number]) {
    gl!.disable(gl!.BLEND);
    P.splat.bind();
    gl!.uniform1i(u("splat", "uTarget"), vel.read.attach(0));
    gl!.uniform1f(u("splat", "aspectRatio"), ar());
    gl!.uniform2f(u("splat", "point"), x, y);
    gl!.uniform3f(u("splat", "color"), dx, dy, 0);
    let rad = C.RADIUS / 100;
    if (ar() > 1) rad *= ar();
    gl!.uniform1f(u("splat", "radius"), rad);
    blit(vel.write); vel.swap();
    gl!.uniform1i(u("splat", "uTarget"), dye.read.attach(0));
    gl!.uniform3f(u("splat", "color"), col[0], col[1], col[2]);
    blit(dye.write); dye.swap();
  }
  const pigment = (hex: string, k = 1): [number, number, number] => {
    const c = [1, 3, 5].map((o) => Math.max(0.02, 1 - parseInt(hex.slice(o, o + 2), 16) / 255) * k);
    return [c[0] ?? 0, c[1] ?? 0, c[2] ?? 0];
  };
  function step(dt: number) {
    gl!.disable(gl!.BLEND);
    P.curl.bind();
    gl!.uniform2f(u("curl", "texelSize"), vel.tx, vel.ty);
    gl!.uniform1i(u("curl", "uVelocity"), vel.read.attach(0));
    blit(curlT);
    P.vorticity.bind();
    gl!.uniform2f(u("vorticity", "texelSize"), vel.tx, vel.ty);
    gl!.uniform1i(u("vorticity", "uVelocity"), vel.read.attach(0));
    gl!.uniform1i(u("vorticity", "uCurl"), curlT.attach(1));
    gl!.uniform1f(u("vorticity", "curl"), C.CURL);
    gl!.uniform1f(u("vorticity", "dt"), dt);
    blit(vel.write); vel.swap();
    P.divergence.bind();
    gl!.uniform2f(u("divergence", "texelSize"), vel.tx, vel.ty);
    gl!.uniform1i(u("divergence", "uVelocity"), vel.read.attach(0));
    blit(divg);
    P.clear.bind();
    gl!.uniform1i(u("clear", "uTexture"), pres.read.attach(0));
    gl!.uniform1f(u("clear", "value"), C.PRESS);
    blit(pres.write); pres.swap();
    P.pressure.bind();
    gl!.uniform2f(u("pressure", "texelSize"), vel.tx, vel.ty);
    gl!.uniform1i(u("pressure", "uDivergence"), divg.attach(0));
    for (let i = 0; i < C.ITER; i++) {
      gl!.uniform1i(u("pressure", "uPressure"), pres.read.attach(1));
      blit(pres.write); pres.swap();
    }
    P.gradient.bind();
    gl!.uniform2f(u("gradient", "texelSize"), vel.tx, vel.ty);
    gl!.uniform1i(u("gradient", "uPressure"), pres.read.attach(0));
    gl!.uniform1i(u("gradient", "uVelocity"), vel.read.attach(1));
    blit(vel.write); vel.swap();
    P.advection.bind();
    const dyeTs = u("advection", "dyeTexelSize");
    gl!.uniform2f(u("advection", "texelSize"), vel.tx, vel.ty);
    if (dyeTs) gl!.uniform2f(dyeTs, vel.tx, vel.ty);
    const vid = vel.read.attach(0);
    gl!.uniform1i(u("advection", "uVelocity"), vid);
    gl!.uniform1i(u("advection", "uSource"), vid);
    gl!.uniform1f(u("advection", "dt"), dt);
    gl!.uniform1f(u("advection", "dissipation"), C.VEL_DISS);
    blit(vel.write); vel.swap();
    if (dyeTs) gl!.uniform2f(dyeTs, dye.tx, dye.ty);
    gl!.uniform1i(u("advection", "uVelocity"), vel.read.attach(0));
    gl!.uniform1i(u("advection", "uSource"), dye.read.attach(1));
    gl!.uniform1f(u("advection", "dissipation"), C.DYE_DISS);
    blit(dye.write); dye.swap();
  }
  function render() {
    P.display.bind();
    gl!.uniform2f(u("display", "texelSize"), 1 / dye.w, 1 / dye.h);
    gl!.uniform1i(u("display", "uTexture"), dye.read.attach(0));
    gl!.uniform3f(u("display", "uBack"), 0.984, 0.973, 0.953); // --paper
    blit(null);
  }
  let colorIdx = 0;
  const nextColor = () => palette[colorIdx++ % palette.length] ?? "#e8322b";
  function randomSplats(n: number) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2, f = 900 + Math.random() * 1400;
      splat(0.15 + Math.random() * 0.7, 0.15 + Math.random() * 0.7, Math.cos(a) * f, Math.sin(a) * f, pigment(nextColor(), 0.9 + Math.random() * 0.5));
    }
  }

  return {
    canvas, splat, step, render, randomSplats, pigment, nextColor, force: C.FORCE,
    resize() { if (size()) init(); },
    // контекст не теряем намеренно: StrictMode перезапускает эффект на том же холсте,
    // а getContext после loseContext вернул бы мёртвый контекст
    dispose() { freeTargets(); },
  };
}
