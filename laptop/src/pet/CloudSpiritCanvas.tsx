import React, { useEffect, useRef } from 'react';

export type SpiritEmotion = 'CURIOUS' | 'FOCUSED' | 'HAPPY' | 'SLEEPY' | 'DISTRACTED';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  alpha: number;
  life: number;
  maxLife: number;
  type: 'sparkle' | 'zzz' | 'heart' | 'alert';
}

export function renderCloudSpirit(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  lookX: number,
  lookY: number,
  scaleX: number,
  scaleY: number,
  emotion: SpiritEmotion,
  particles: Particle[] = []
) {
  ctx.clearRect(0, 0, w, h);
  ctx.save();
  ctx.translate(w / 2, h / 2);
  ctx.scale(scaleX, scaleY);

  // 1. Soft Ambient Mint/Sky Aura
  const aura = ctx.createRadialGradient(0, 0, 10, 0, 0, w * 0.46);
  if (emotion === 'FOCUSED') {
    aura.addColorStop(0, 'rgba(122, 214, 86, 0.40)');
    aura.addColorStop(0.6, 'rgba(122, 214, 86, 0.12)');
  } else if (emotion === 'HAPPY') {
    aura.addColorStop(0, 'rgba(255, 215, 0, 0.38)');
    aura.addColorStop(0.6, 'rgba(122, 214, 86, 0.18)');
  } else if (emotion === 'SLEEPY') {
    aura.addColorStop(0, 'rgba(147, 197, 253, 0.28)');
    aura.addColorStop(0.6, 'rgba(199, 210, 254, 0.10)');
  } else if (emotion === 'DISTRACTED') {
    aura.addColorStop(0, 'rgba(244, 63, 94, 0.40)');
    aura.addColorStop(0.6, 'rgba(251, 146, 60, 0.16)');
  } else {
    aura.addColorStop(0, 'rgba(124, 200, 248, 0.35)');
    aura.addColorStop(0.6, 'rgba(124, 200, 248, 0.12)');
  }
  aura.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.fillStyle = aura;
  ctx.beginPath();
  ctx.arc(0, 0, w * 0.46, 0, Math.PI * 2);
  ctx.fill();

  // 2. Plump Multi-Puff 3D Clay Cloud Silhouette
  const bodyGrad = ctx.createRadialGradient(-w * 0.1, -h * 0.12, 4, 0, 4, w * 0.36);
  bodyGrad.addColorStop(0, '#FFFFFF');
  if (emotion === 'FOCUSED') {
    bodyGrad.addColorStop(0.35, '#E8F3E8');
    bodyGrad.addColorStop(0.75, '#9FE855');
    bodyGrad.addColorStop(1, '#4E9E26');
  } else if (emotion === 'HAPPY') {
    bodyGrad.addColorStop(0.35, '#FEF9C3');
    bodyGrad.addColorStop(0.75, '#A3E635');
    bodyGrad.addColorStop(1, '#65A30D');
  } else if (emotion === 'SLEEPY') {
    bodyGrad.addColorStop(0.35, '#EFF6FF');
    bodyGrad.addColorStop(0.75, '#93C5FD');
    bodyGrad.addColorStop(1, '#3B82F6');
  } else if (emotion === 'DISTRACTED') {
    bodyGrad.addColorStop(0.35, '#FFF1F2');
    bodyGrad.addColorStop(0.75, '#FDA4AF');
    bodyGrad.addColorStop(1, '#F43F5E');
  } else {
    bodyGrad.addColorStop(0.35, '#E0F2FE');
    bodyGrad.addColorStop(0.75, '#38BDF8');
    bodyGrad.addColorStop(1, '#0284C7');
  }

  ctx.fillStyle = bodyGrad;
  ctx.beginPath();
  ctx.arc(-20, 6, 22, 0, Math.PI * 2); // Left puff
  ctx.arc(20, 6, 22, 0, Math.PI * 2);  // Right puff
  ctx.arc(0, -11, 26, 0, Math.PI * 2); // Top center dome
  if (ctx.roundRect) {
    ctx.roundRect(-30, -5, 60, 30, 15);  // Base bridge
  } else {
    ctx.rect(-30, -5, 60, 30);
  }
  ctx.fill();

  // Soft bottom ambient shadow rim inside the cloud
  ctx.fillStyle = 'rgba(15, 23, 42, 0.08)';
  ctx.beginPath();
  ctx.ellipse(0, 18, 28, 6, 0, 0, Math.PI * 2);
  ctx.fill();

  // 3. Expressive Sclera Eyes & Pupil Tracking
  const dx = Math.max(-6, Math.min(6, lookX * 6));
  const dy = Math.max(-4, Math.min(4, lookY * 4));

  [-12, 12].forEach((eyeX) => {
    // White sclera
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.ellipse(
      eyeX + dx * 0.35,
      -3 + dy * 0.35,
      8,
      emotion === 'SLEEPY' ? 2.6 : 9,
      0,
      0,
      Math.PI * 2
    );
    ctx.fill();

    // Eye outline
    ctx.strokeStyle = 'rgba(18, 20, 23, 0.12)';
    ctx.lineWidth = 1;
    ctx.stroke();

    if (emotion !== 'SLEEPY') {
      // Deep charcoal pupil
      ctx.fillStyle = '#121417';
      ctx.beginPath();
      ctx.arc(eyeX + dx, -3 + dy, 4.4, 0, Math.PI * 2);
      ctx.fill();

      // Primary Specular Catchlight
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(eyeX + dx - 1.5, -5 + dy, 1.6, 0, Math.PI * 2);
      ctx.fill();

      // Secondary micro reflection
      ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
      ctx.beginPath();
      ctx.arc(eyeX + dx + 1.2, -1.5 + dy, 0.9, 0, Math.PI * 2);
      ctx.fill();
    }
  });

  // 4. Cheerful Rosy Cheeks
  if (emotion === 'HAPPY' || emotion === 'CURIOUS') {
    ctx.fillStyle = 'rgba(244, 63, 94, 0.22)';
    ctx.beginPath();
    ctx.arc(-22, 6, 6, 0, Math.PI * 2);
    ctx.arc(22, 6, 6, 0, Math.PI * 2);
    ctx.fill();
  }

  // 5. Tactile Mouth
  ctx.strokeStyle = '#121417';
  ctx.lineWidth = 2.4;
  ctx.lineCap = 'round';
  ctx.beginPath();
  if (emotion === 'HAPPY') {
    ctx.arc(dx * 0.4, 6 + dy * 0.2, 5.5, 0.1 * Math.PI, 0.9 * Math.PI);
  } else if (emotion === 'SLEEPY') {
    ctx.arc(dx * 0.4, 7 + dy * 0.2, 3, 0, Math.PI);
  } else if (emotion === 'DISTRACTED') {
    // Worried wavy mouth
    ctx.moveTo(-5 + dx * 0.4, 7 + dy * 0.2);
    ctx.quadraticCurveTo(-2 + dx * 0.4, 4 + dy * 0.2, 0 + dx * 0.4, 7 + dy * 0.2);
    ctx.quadraticCurveTo(2 + dx * 0.4, 10 + dy * 0.2, 5 + dx * 0.4, 7 + dy * 0.2);
  } else {
    ctx.arc(dx * 0.4, 6 + dy * 0.2, 4.5, 0.15 * Math.PI, 0.85 * Math.PI);
  }
  ctx.stroke();

  ctx.restore();

  // 6. Render Floating Particles (Unscaled Stage Overlay)
  if (particles && particles.length > 0) {
    ctx.save();
    ctx.translate(w / 2, h / 2);
    particles.forEach((p) => {
      ctx.save();
      ctx.globalAlpha = Math.max(0, p.alpha);
      ctx.translate(p.x, p.y);

      if (p.type === 'sparkle') {
        // 4-point sparkle star
        ctx.fillStyle = '#FBBF24';
        ctx.beginPath();
        const r = p.size;
        ctx.moveTo(0, -r);
        ctx.quadraticCurveTo(0, 0, r, 0);
        ctx.quadraticCurveTo(0, 0, 0, r);
        ctx.quadraticCurveTo(0, 0, -r, 0);
        ctx.quadraticCurveTo(0, 0, 0, -r);
        ctx.fill();
      } else if (p.type === 'zzz') {
        ctx.fillStyle = '#60A5FA';
        ctx.font = `bold ${Math.round(p.size)}px sans-serif`;
        ctx.fillText('z', 0, 0);
      } else if (p.type === 'heart') {
        ctx.fillStyle = '#F43F5E';
        ctx.beginPath();
        const s = p.size * 0.5;
        ctx.moveTo(0, s * 0.4);
        ctx.bezierCurveTo(-s, -s * 0.6, -s * 1.4, s * 0.4, 0, s * 1.4);
        ctx.bezierCurveTo(s * 1.4, s * 0.4, s, -s * 0.6, 0, s * 0.4);
        ctx.fill();
      } else if (p.type === 'alert') {
        // Amber warning dot
        ctx.fillStyle = '#F59E0B';
        ctx.beginPath();
        ctx.arc(0, 0, p.size * 0.4, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    });
    ctx.restore();
  }
}

interface CloudSpiritCanvasProps {
  emotion?: SpiritEmotion;
  width?: number;
  height?: number;
  interactive?: boolean;
  onPet?: () => void;
}

export function CloudSpiritCanvas({
  emotion = 'CURIOUS',
  width = 180,
  height = 140,
  interactive = true,
  onPet
}: CloudSpiritCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const lookPos = useRef({ x: 0, y: 0 });
  const animFrame = useRef<number | null>(null);

  // Physics simulation references
  const scaleRef = useRef({ x: 1, y: 1 });
  const velRef = useRef({ x: 0, y: 0 });
  const particlesRef = useRef<Particle[]>([]);
  const isMouseDown = useRef(false);
  const lastMousePos = useRef({ x: 0, y: 0 });

  // Spawn sparkle or Zzz particles
  const spawnParticles = (type: 'sparkle' | 'zzz' | 'heart' | 'alert', count = 3) => {
    for (let i = 0; i < count; i++) {
      particlesRef.current.push({
        x: (Math.random() - 0.5) * 40,
        y: -10 + (Math.random() - 0.5) * 20,
        vx: (Math.random() - 0.5) * 1.6,
        vy: -0.8 - Math.random() * 1.4,
        size: type === 'zzz' ? 10 + Math.random() * 4 : 5 + Math.random() * 5,
        alpha: 1.0,
        life: 0,
        maxLife: 40 + Math.random() * 30,
        type
      });
    }
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let time = 0;
    const render = () => {
      time += 0.04;

      // 1. Spring-damped mass physics for squash-and-stretch
      const targetScaleX = isMouseDown.current ? 1.25 : 1.0;
      const targetScaleY = isMouseDown.current ? 0.78 : 1.0;

      // Harmonic spring force
      const k = 0.16; // spring stiffness
      const damping = 0.78; // friction

      const ax = (targetScaleX - scaleRef.current.x) * k;
      const ay = (targetScaleY - scaleRef.current.y) * k;

      velRef.current.x = (velRef.current.x + ax) * damping;
      velRef.current.y = (velRef.current.y + ay) * damping;

      scaleRef.current.x += velRef.current.x;
      scaleRef.current.y += velRef.current.y;

      // Gentle idle breathing layer
      const idleBreathing = Math.sin(time) * 0.024;
      const finalScaleX = scaleRef.current.x + idleBreathing;
      const finalScaleY = scaleRef.current.y - idleBreathing;

      // 2. Periodic particle spawning based on emotion
      if (Math.random() < 0.03) {
        if (emotion === 'HAPPY') spawnParticles('sparkle', 1);
        if (emotion === 'SLEEPY') spawnParticles('zzz', 1);
        if (emotion === 'DISTRACTED') spawnParticles('alert', 1);
      }

      // 3. Update active particles
      particlesRef.current.forEach((p) => {
        p.x += p.vx;
        p.y += p.vy;
        p.life += 1;
        p.alpha = 1 - p.life / p.maxLife;
      });
      particlesRef.current = particlesRef.current.filter((p) => p.life < p.maxLife);

      // 4. Render
      renderCloudSpirit(
        ctx,
        width,
        height,
        lookPos.current.x,
        lookPos.current.y,
        finalScaleX,
        finalScaleY,
        emotion,
        particlesRef.current
      );

      animFrame.current = requestAnimationFrame(render);
    };

    animFrame.current = requestAnimationFrame(render);
    return () => {
      if (animFrame.current) cancelAnimationFrame(animFrame.current);
    };
  }, [emotion, width, height]);

  // Interactive mouse handlers for petting & squishing
  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!interactive) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const nx = ((e.clientX - rect.left) / rect.width) * 2 - 1;
    const ny = ((e.clientY - rect.top) / rect.height) * 2 - 1;
    lookPos.current = { x: nx, y: ny };

    // Detect fast petting/rubbing motion
    const speed = Math.hypot(e.clientX - lastMousePos.current.x, e.clientY - lastMousePos.current.y);
    if (speed > 16 && Math.random() < 0.25) {
      spawnParticles('sparkle', 1);
      if (onPet) onPet();
    }
    lastMousePos.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseDown = () => {
    if (!interactive) return;
    isMouseDown.current = true;
    velRef.current = { x: 0.1, y: -0.1 }; // Initial tactile kick
    spawnParticles('heart', 2);
    if (onPet) onPet();
  };

  const handleMouseUp = () => {
    isMouseDown.current = false;
    velRef.current = { x: -0.12, y: 0.16 }; // Spring release bounce
  };

  const handleMouseLeave = () => {
    isMouseDown.current = false;
    lookPos.current = { x: 0, y: 0 };
  };

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      style={{
        display: 'block',
        margin: '0 auto',
        cursor: interactive ? 'pointer' : 'default',
        touchAction: 'none'
      }}
      onMouseMove={handleMouseMove}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseLeave}
    />
  );
}
