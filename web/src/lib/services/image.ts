const MAX_SIDE = 1600;
const JPEG_QUALITY = 0.85;

/**
 * Сжатие фото на клиенте до отправки (F-4.2): длинная сторона 1600px, JPEG. EXIF-ориентация
 * применяется через createImageBitmap({imageOrientation:'from-image'}) — иначе фото с iPhone лягут
 * боком. Дополнительно применяется ручной поворот rotate (0/90/180/270) из превью.
 */
export async function compressImage(file: File, rotate = 0): Promise<Blob> {
  const bitmap = await loadBitmap(file);
  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const w = Math.round(bitmap.width * scale);
    const h = Math.round(bitmap.height * scale);
    const quarter = ((rotate % 360) + 360) % 360;
    const swap = quarter === 90 || quarter === 270;

    const canvas = document.createElement("canvas");
    canvas.width = swap ? h : w;
    canvas.height = swap ? w : h;
    const ctx = canvas.getContext("2d");
    if (!ctx) return file;

    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.rotate((quarter * Math.PI) / 180);
    ctx.drawImage(bitmap, -w / 2, -h / 2, w, h);

    return await new Promise<Blob>((resolve) =>
      canvas.toBlob((blob) => resolve(blob ?? file), "image/jpeg", JPEG_QUALITY),
    );
  } finally {
    bitmap.close?.();
  }
}

async function loadBitmap(file: File): Promise<ImageBitmap> {
  try {
    return await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    // старый браузер без imageOrientation — грузим как есть (без EXIF-коррекции)
    return await createImageBitmap(file);
  }
}
