import React, { useEffect, useRef } from 'react';

export default function InteractiveCanvas() {
  const canvasRef = useRef(null);
  const particlesRef = useRef([]);
  const beamsRef = useRef([]);
  const mouseRef = useRef({ x: 0, y: 0, isMoving: false });
  const timerRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');

    const resizeCanvas = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    // --- 1. 修改配置：让颜色更深，方便观测 ---
    const createBeam = (targetX, targetY) => {
      const width = window.innerWidth;
      const height = window.innerHeight;

      const edge = Math.floor(Math.random() * 4);
      let startX, startY;

      // 让起始点稍微远一点，从屏幕外射入
      const offset = 100;

      switch(edge) {
        case 0: startX = Math.random() * width; startY = -offset; break; // 上
        case 1: startX = width + offset; startY = Math.random() * height; break; // 右
        case 2: startX = Math.random() * width; startY = height + offset; break; // 下
        case 3: startX = -offset; startY = Math.random() * height; break; // 左
      }

      const dx = targetX - startX;
      const dy = targetY - startY;
      const distance = Math.sqrt(dx * dx + dy * dy);
      const velocity = 10; // 稍微加快速度

      beamsRef.current.push({
        x: startX,
        y: startY,
        vx: (dx / distance) * velocity,
        vy: (dy / distance) * velocity,
        life: 200,
        length: 750, // 增加长度
        // 关键修改：使用深青色/蓝色，而不是随机浅色，确保对比度
        color: `hsl(${Math.random() * 50 + 190}, 50%, 75%)`      });
    };

    const handleMouseMove = (e) => {
      const { clientX, clientY } = e;
      mouseRef.current = { x: clientX, y: clientY, isMoving: true };

      particlesRef.current.push({
        x: clientX,
        y: clientY,
        size: 15,
        life: 0.7 // 稍微降低一点透明度，让它柔和些
      });

      clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        mouseRef.current.isMoving = false;
      }, 100);
    };

    const handleClick = (e) => {
      // 连续发射 5 条，增加视觉密度
      for(let i=0; i<5; i++) {
        setTimeout(() => {
          createBeam(e.clientX, e.clientY);
        }, i * 40);
      }
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('click', handleClick);

    // --- 动画循环 ---
    const animate = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // A. 绘制鼠标轨迹
      for (let i = particlesRef.current.length - 1; i >= 0; i--) {
        const p = particlesRef.current[i];
        p.life -= 0.02;
        p.size -= 0.5;

        if (p.life <= 0 || p.size <= 0) {
          particlesRef.current.splice(i, 1);
          continue;
        }

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${p.life})`;
        ctx.fill();
      }

      // B. 绘制射线 (修复不可见的问题)
      for (let i = beamsRef.current.length - 1; i >= 0; i--) {
        const b = beamsRef.current[i];
        b.x += b.vx;
        b.y += b.vy;
        b.life--;

        if (b.life < -50) {
          beamsRef.current.splice(i, 1);
          continue;
        }

        ctx.beginPath();
        const tailX = b.x - (b.vx / 25) * b.length;
        const tailY = b.y - (b.vy / 25) * b.length;

        ctx.moveTo(b.x, b.y);
        ctx.lineTo(tailX, tailY);

        // 关键修复 1：加粗线条，防止被模糊滤镜完全吞噬
        ctx.lineWidth = 13;
        ctx.lineCap = 'round';

        // 关键修复 2：使用我们生成的颜色，而不是白色
        ctx.strokeStyle = b.color;
        ctx.stroke();
      }

      requestAnimationFrame(animate);
    };

    const animId = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener('resize', resizeCanvas);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('click', handleClick);
      cancelAnimationFrame(animId);
    };
  }, []);

  return (
      <>
        <svg style={{ position: 'absolute', width: 0, height: 0 }}>
          <filter id="goo-canvas">
            {/* 稍微降低标准差，让细节更容易保留 */}
            <feGaussianBlur in="SourceGraphic" result="blur" stdDeviation="10" />
            <feColorMatrix
                in="blur"
                mode="matrix"
                values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -9"
                result="goo"
            />
            <feComposite in="SourceGraphic" in2="goo" operator="atop"/>
          </filter>
        </svg>

        <canvas
            ref={canvasRef}
            className="fixed inset-0 pointer-events-none z-0"
            style={{ filter: 'url(#goo-canvas)' }}
        />
      </>
  );
}