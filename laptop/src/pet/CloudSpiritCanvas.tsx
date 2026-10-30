import React, { useEffect, useRef } from 'react';

export type SpiritEmotion = 'CURIOUS' | 'FOCUSED' | 'HAPPY' | 'SLEEPY';

export function renderCloudSpirit(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  lookX: number,
  lookY: number,
  scaleX: number,
  scaleY: number,
  emotion: SpiritEmotion
) {
  ctx.clearRect(0, 0, w, h);
  ctx.save();
  ctx.translate(w / 2, h / 2);
  ctx.scale(scaleX, scaleY);

  // 1. Soft Ambient Mint/Sky Aura
  const aura = ctx.createRadialGradient(0, 0, 10, 0, 0, w * 0.45);
  aura.addColorStop(0, emotion === 'FOCUSED' ? 'rgba(124, 232, 158, 0.38)' : 'rgba(124, 200, 248, 0.32)');
  aura.addColorStop(1, 'rgba(124, 232, 158, 0)');
  ctx.fillStyle = aura;
  ctx.beginPath();
  ctx.arc(0, 0, w * 0.45, 0, Math.PI * 2);
  ctx.fill();

  // 2. Plump Multi-Puff 3D Clay Cloud Silhouette
  const bodyGrad = ctx.createRadialGradient(-w * 0.1, -h * 0.12, 4, 0, 4, w * 0.34);
  bodyGrad.addColorStop(0, '#E8FFF0');
  bodyGrad.addColorStop(0.55, emotion === 'FOCUSED' ? '#7CE89E' : '#89D4FA');
  bodyGrad.addColorStop(1, emotion === 'FOCUSED' ? '#34B371' : '#3B9EDB');

  ctx.fillStyle = bodyGrad;
  ctx.beginPath();
  ctx.arc(-18, 6, 20, 0, Math.PI * 2); // Left puff
  ctx.arc(18, 6, 20, 0, Math.PI * 2);  // Right puff
  ctx.arc(0, -10, 24, 0, Math.PI * 2); // Top center dome
  ctx.roundRect(-28, -4, 56, 28, 14);  // Base bridge
  ctx.fill();

  // 3. Expressive Sclera Eyes & Pupil Tracking
  const dx = Math.max(-6, Math.min(6, lookX * 6));
  const dy = Math.max(-4, Math.min(4, lookY * 4));

  [-11, 11].forEach((eyeX) => {
    // White sclera
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.ellipse(eyeX + dx * 0.4, -2 + dy * 0.4, 7.5, emotion === 'SLEEPY' ? 2.5 : 8.5, 0, 0, Math.PI * 2);
    ctx.fill();

    if (emotion !== 'SLEEPY') {
      // Deep charcoal pupil
      ctx.fillStyle = '#121417';
      ctx.beginPath();
      ctx.arc(eyeX + dx, -2 + dy, 4.2, 0, Math.PI * 2);
      ctx.fill();

      // White specular catchlight
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(eyeX + dx - 1.5, -4 + dy, 1.4, 0, Math.PI * 2);
      ctx.fill();
    }
  });

  // 4. Cute Center Smile
  ctx.strokeStyle = '#121417';
  ctx.lineWidth = 2.2;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.arc(dx * 0.5, 6 + dy * 0.3, 4.5, 0.15 * Math.PI, 0.85 * Math.PI);
  ctx.stroke();

  ctx.restore();
}

interface CloudSpiritCanvasProps {
  emotion?: SpiritEmotion;
  width?: number;
  height?: number;
  interactive?: boolean;
}

export function CloudSpiritCanvas({
  emotion = 'CURIOUS',
  width = 160,
  height = 130,
  interactive = true
}: CloudSpiritCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const lookPos = useRef({ x: 0, y: 0 });
  const animFrame = useRef<number | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let time = 0;
    const render = () => {
      time += 0.04;
      // Gentle breathing idle squash & stretch
      const breathe = Math.sin(time) * 0.03;
      const scaleX = 1 + breathe;
      const scaleY = 1 - breathe;

      renderCloudSpirit(
        ctx,
        width,
        height,
        lookPos.current.x,
        lookPos.current.y,
        scaleX,
        scaleY,
        emotion
      );

      animFrame.current = requestAnimationFrame(render);
    };

    animFrame.current = requestAnimationFrame(render);
    return () => {
      if (animFrame.current) cancelAnimationFrame(animFrame.current);
    };
  }, [emotion, width, height]);

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!interactive) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const nx = (e.clientX - rect.left) / rect.width * 2 - 1;
    const ny = (e.clientY - rect.top) / rect.height * 2 - 1;
    lookPos.current = { x: nx, y: ny };
  };

  const handleMouseLeave = () => {
    lookPos.current = { x: 0, y: 0 };
  };

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      style={{ display: 'block', margin: '0 auto', cursor: interactive ? 'pointer' : 'default' }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    />
  );
}
