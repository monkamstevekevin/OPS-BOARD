export function lineChart(canvas, data, opts) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width = canvas.clientWidth * devicePixelRatio;
  const h = canvas.height = canvas.clientHeight * devicePixelRatio;
  ctx.scale(devicePixelRatio, devicePixelRatio);

  const pad = 30; // padding for axes
  const innerW = canvas.clientWidth - pad * 2;
  const innerH = canvas.clientHeight - pad * 2;

  const xs = data.map(d => +new Date(d.ts));
  const ys = data.map(d => d.v);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = opts.yMin != null ? opts.yMin : Math.min(...ys);
  const maxY = opts.yMax != null ? opts.yMax : Math.max(...ys);
  const spanX = (maxX - minX) || 1;
  const spanY = (maxY - minY) || 1;

  const xToPx = (x) => pad + ( (x - minX) / spanX ) * innerW;
  const yToPx = (y) => pad + innerH - ( (y - minY) / spanY ) * innerH;

  // bg
  ctx.fillStyle = '#0f172a';
  ctx.fillRect(0, 0, canvas.clientWidth, canvas.clientHeight);

  // axes
  ctx.strokeStyle = '#223152';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(pad, pad);
  ctx.lineTo(pad, pad + innerH);
  ctx.lineTo(pad + innerW, pad + innerH);
  ctx.stroke();

  // grid (3 horizontal)
  ctx.strokeStyle = '#1a2540';
  for (let i = 1; i <= 3; i++) {
    const y = pad + (innerH * i) / 4;
    ctx.beginPath();
    ctx.moveTo(pad, y);
    ctx.lineTo(pad + innerW, y);
    ctx.stroke();
  }

  // line
  ctx.strokeStyle = opts.color || '#0ea5e9';
  ctx.lineWidth = 2;
  ctx.beginPath();
  data.forEach((d, i) => {
    const x = xToPx(+new Date(d.ts));
    const y = yToPx(d.v);
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();

  // labels
  ctx.fillStyle = '#94a3b8';
  ctx.font = '12px system-ui';
  ctx.fillText(String(minY.toFixed(0)), 4, pad + innerH);
  ctx.fillText(String(maxY.toFixed(0)), 4, pad + 12);
}

