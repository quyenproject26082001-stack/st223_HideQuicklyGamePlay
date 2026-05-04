/**
 * Tạo collision mask PNG cho Map/1.jpg
 * Đen = tường | Trắng = đi được | Đỏ = cửa
 *
 * Phân tích map 1 (tỉ lệ ~2:3, top-down isometric):
 *  - Top-left: notch cắt góc (~17% rộng, ~22% cao)
 *  - Phần trên: phòng khách + bếp open-plan (~37% cao)
 *  - Giữa: hành lang dọc (~43-55% ngang, từ 37% xuống dưới)
 *  - Trái hành lang: 3 phòng xếp chồng
 *  - Phải hành lang: 2 phòng + nhà tắm
 */

const zlib = require('zlib');
const fs   = require('fs');

// ---------- PNG encoder (no external deps) ----------
const crcTable = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) c = (c & 1) ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    t[i] = c;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}

function pngChunk(type, data) {
  const t = Buffer.from(type);
  const len = Buffer.allocUnsafe(4); len.writeUInt32BE(data.length);
  const crcVal = Buffer.allocUnsafe(4);
  crcVal.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crcVal]);
}

function encodePng(width, height, rgbPixels) {
  const raw = Buffer.alloc(height * (1 + width * 3));
  for (let y = 0; y < height; y++) {
    raw[y * (1 + width * 3)] = 0; // filter: None
    rgbPixels.copy(raw, y * (1 + width * 3) + 1, y * width * 3, (y + 1) * width * 3);
  }
  const ihdr = Buffer.allocUnsafe(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8]=8; ihdr[9]=2; ihdr[10]=0; ihdr[11]=0; ihdr[12]=0;
  return Buffer.concat([
    Buffer.from([137,80,78,71,13,10,26,10]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0))
  ]);
}

// ---------- Canvas API ----------
const W = 200, H = 320;
const px = Buffer.alloc(W * H * 3, 255); // fill trắng

function setPixel(x, y, r, g, b) {
  if (x < 0 || x >= W || y < 0 || y >= H) return;
  const i = (y * W + x) * 3;
  px[i] = r; px[i+1] = g; px[i+2] = b;
}
function rect(x1, y1, x2, y2, col) {
  const [r,g,b] = col;
  for (let y = Math.max(0,y1); y < Math.min(H,y2); y++)
    for (let x = Math.max(0,x1); x < Math.min(W,x2); x++)
      setPixel(x,y,r,g,b);
}

const BLK = [0,0,0];       // tường
const RED = [255,0,0];     // cửa
// trắng = đi được (mặc định)

// =========================================================
//  MAP 1 — collision layout (200 × 320 px)
// =========================================================

// ── Tường ngoài ──────────────────────────────────────────
rect(  0,  0, 200,   5, BLK); // top wall  (được cắt bởi notch bên dưới)
rect(  0,  0,  35,  71, BLK); // notch góc trên-trái
rect(  0, 71,   5, 315, BLK); // left wall
rect(195,  0, 200, 315, BLK); // right wall
rect(  0,315, 200, 320, BLK); // bottom wall

// ── Hành lang (corridor) dọc ─────────────────────────────
// trái hành lang: x=88-93 | phải: x=107-112
rect( 88, 71,  93, 315, BLK); // vách trái hành lang
rect(107, 71, 112, 315, BLK); // vách phải hành lang

// ── Tường ngang — bên trái hành lang ─────────────────────
// Phân cách top living → phòng 1
rect(  5,118,  88, 123, BLK);
// Phân cách phòng 1 → phòng 2
rect(  5,185,  88, 190, BLK);
// Phân cách phòng 2 → phòng 3
rect(  5,240,  88, 245, BLK);

// ── Tường ngang — bên phải hành lang ─────────────────────
// Phân cách top → nhà tắm phải
rect(112,158, 195, 163, BLK);
// Phân cách nhà tắm → phòng ngủ phải
rect(112,240, 195, 245, BLK);

// ── Cửa (gap màu đỏ trong tường) ─────────────────────────
// Lối vào hành lang từ phòng khách
rect( 88, 82,  93, 106, RED);

// Cửa phòng 1 (trái)
rect( 28,118,  52, 123, RED);
// Cửa phòng 2 (trái)
rect( 28,185,  52, 190, RED);
// Cửa phòng 3 (trái)
rect( 28,240,  52, 245, RED);

// Cửa nhà tắm (phải)
rect(130,158, 154, 163, RED);
// Cửa phòng ngủ phải
rect(130,240, 154, 245, RED);

// ── Spawn points (màu xanh lá) ───────────────────────────
// Vị trí mặc định để đặt nhân vật
// (chỉ dùng để xác định spawn, không ảnh hưởng collision)
// Killer spawn: giữa phòng khách
setPixel(60, 85, 0,200,0);
// Hider spawns
setPixel(28, 148, 0,200,0); // phòng 1
setPixel(28, 210, 0,200,0); // phòng 2
setPixel(28, 260, 0,200,0); // phòng 3
setPixel(148,195, 0,200,0); // nhà tắm phải
setPixel(148,270, 0,200,0); // phòng ngủ phải

// =========================================================

const outPath = 'C:/APPS/st223/ST222_Poster-Maker-2/app/src/main/assets/Map/1_collision.png';
fs.writeFileSync(outPath, encodePng(W, H, px));
console.log(`✓ Saved: ${outPath}  (${W}×${H}px)`);
console.log('  Đen=tường | Trắng=đi được | Đỏ=cửa | Xanh=spawn');
