import wave
import struct
import math
import random

sample_rate = 44100

def generate_ticket_punch(filename):
    duration = 0.38
    num_samples = int(sample_rate * duration)
    samples = []
    
    random.seed(42)
    
    for i in range(num_samples):
        t = i / sample_rate
        val = 0.0
        
        # 1. Mechanical punch / snap (t: 0.005 to 0.08)
        if 0.005 <= t < 0.08:
            t_snap = t - 0.005
            env_click = math.exp(-t_snap * 160)
            click = math.sin(2 * math.pi * 3400 * t_snap) * 0.6 + math.sin(2 * math.pi * 5200 * t_snap) * 0.4
            
            env_noise = math.exp(-t_snap * 90) * (1.0 - math.exp(-t_snap * 800))
            noise = (random.random() * 2.0 - 1.0) * env_noise * 0.7
            
            env_body = math.exp(-t_snap * 50)
            body = math.sin(2 * math.pi * 1600 * t_snap) * 0.4 * env_body
            
            val += (click * env_click + noise + body) * 0.85
            
        # 2. Gate chime "ding" (crisp HSR ticket barrier confirmation)
        if t >= 0.045:
            t_chime = t - 0.045
            env_chime = math.exp(-t_chime * 12)
            chime = (math.sin(2 * math.pi * 1174.66 * t_chime) * 0.4 +
                     math.sin(2 * math.pi * 1760.0 * t_chime) * 0.3 +
                     math.sin(2 * math.pi * 2349.32 * t_chime) * 0.15) * env_chime
            val += chime * 0.45
            
        val = max(-1.0, min(1.0, val))
        samples.append(int(val * 32767))
        
    with wave.open(filename, 'w') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        data = struct.pack(f'<{len(samples)}h', *samples)
        wav.writeframes(data)
    print("Generated " + filename)

generate_ticket_punch("app/src/main/res/raw/ticket_punch.wav")
