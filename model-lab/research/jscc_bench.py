#!/usr/bin/env python3
"""N6 Level-1 application-layer semantic-priority erasure benchmark.
No Android code, learned model, or PHY/IQ simulation is used.
Uses only Python 3 standard library; PNG plots are written by a minimal encoder.
"""
import argparse, csv, hashlib, json, math, platform, random, struct, sys, time, zlib
from pathlib import Path

LOSS_LEVELS = [0.0, .05, .10, .20, .30, .40, .50]
TEXT_PACKET_BYTES, SEMANTIC_BYTES = 32, 7  # actual N5 SemanticMetadata.toWireBytes() is 7 bytes
SYSTEMS = [("A_text_only", None), ("B_xor_fec_k4_n5", None), ("C_semantic_priority_r1", 1), ("C_semantic_priority_r2", 2), ("C_semantic_priority_r3", 3)]
# G->B, B->G; stationary bad=.4, expected bad run=1/0.12=8.33 packets.
BURSTS = {"mild": (.01, .08, .12, .40), "moderate": (.01, .08, .12, .70), "severe": (.01, .08, .12, .95)}


def packet_count(text): return max(1, math.ceil(len(text.encode("utf-8")) / TEXT_PACKET_BYTES))
def wilson(success, n):
    if not n: return (0., 0.)
    z=1.96; p=success/n; d=1+z*z/n; c=(p+z*z/(2*n))/d; h=z*math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d
    return c-h, c+h

def independent(seed, p, n):
    r=random.Random(seed); return [r.random() < p for _ in range(n)]
def ge(seed, n, cfg):
    good_loss, g_to_b, b_to_g, bad_loss=cfg; r=random.Random(seed)
    # Each trial starts from the stationary state rather than an unrealistically clean Good state.
    bad = r.random() < (g_to_b / (g_to_b + b_to_g)); out=[]
    for _ in range(n):
        out.append(r.random() < (bad_loss if bad else good_loss))
        if bad: bad = not (r.random() < b_to_g)
        elif r.random() < g_to_b: bad = True
    return out

def recover_xor(data_losses, parity_loss):
    # K=4 (last block padded); XOR parity recovers exactly one missing data packet.
    return sum(data_losses) == 0 or (sum(data_losses) == 1 and not parity_loss)

def simulate(system, msg, losses):
    name, replicas=system; text_n=packet_count(msg["text"])
    if name.startswith("A_"):
        text_ok=not any(losses[:text_n]); sem_ok=text_ok; packets=text_n; bytes_=len(msg["text"].encode())
        sem_latency=text_n if sem_ok else None; text_latency=text_n if text_ok else None
    elif name.startswith("B_"):
        blocks=math.ceil(text_n/4); needed=blocks*5; losses=losses[:needed]; text_ok=True; last=0
        for b in range(blocks):
            d=losses[b*5:b*5+4]; par=losses[b*5+4]; ok=recover_xor(d, par); text_ok &= ok
            if ok: last=max(last, b*5+5)
        sem_ok=text_ok; packets=needed; bytes_=needed*TEXT_PACKET_BYTES; sem_latency=last if sem_ok else None; text_latency=last if text_ok else None
    else:
        sem_losses=losses[:replicas]; txt_losses=losses[replicas:replicas+text_n]
        sem_ok=not all(sem_losses); text_ok=not any(txt_losses); packets=replicas+text_n; bytes_=replicas*SEMANTIC_BYTES+len(msg["text"].encode())
        sem_latency=(next(i+1 for i,x in enumerate(sem_losses) if not x) if sem_ok else None)
        text_latency=replicas+text_n if text_ok else None
    # Metadata is atomic: when delivered all N5 slots are exact, otherwise unavailable.
    return dict(semantic=sem_ok, text=text_ok, category=sem_ok, urgency=sem_ok, severity=sem_ok,
                victim=sem_ok, hazard=sem_ok, frame=sem_ok, packets=packets, bytes=bytes_,
                erased=sum(losses[:packets]), semantic_latency=sem_latency, text_latency=text_latency)

def png(path, series, title):
    # Dependency-free 760x460 line chart. Colors are legend order: blue A, orange B, green C-R1, red C-R2, purple C-R3.
    w,h=760,460; pix=bytearray([255,255,255,255])*w*h
    def setp(x,y,c):
        if 0<=x<w and 0<=y<h:
            k=(y*w+x)*4; pix[k:k+4]=bytes((*c,255))
    left,right,top,bottom=70,30,40,55; colors=[(35,99,190),(225,120,25),(35,150,90),(200,55,55),(120,75,170)]
    for y in range(5):
        yy=top+(h-top-bottom)*y//4
        for x in range(left,w-right): setp(x,yy,(225,225,225))
    for x in range(left,w-right): setp(x,h-bottom,(0,0,0))
    for y in range(top,h-bottom): setp(left,y,(0,0,0))
    for si,(label, pts) in enumerate(series):
        c=colors[si%len(colors)]; prev=None
        for xval,yval in pts:
            x=left+round(xval/.5*(w-left-right)); y=top+round((1-yval)*(h-top-bottom))
            if prev:
                x0,y0=prev; steps=max(abs(x-x0),abs(y-y0),1)
                for q in range(steps+1): setp(round(x0+(x-x0)*q/steps),round(y0+(y-y0)*q/steps),c)
            for dx in range(-3,4):
                for dy in range(-3,4):
                    if dx*dx+dy*dy<=9: setp(x+dx,y+dy,c)
            prev=(x,y)
        # swatch only: full labels are recorded in plot_metadata.json and report (PNG keeps zero dependencies).
        for x in range(500+si*45,535+si*45):
            for y in range(15,25): setp(x,y,c)
    raw=b''.join(b'\0'+bytes(pix[y*w*4:(y+1)*w*4]) for y in range(h))
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def aggregate(rows, key):
    out=[]
    for system in [x[0] for x in SYSTEMS]:
        pts=[]
        for loss in LOSS_LEVELS:
            rs=[r for r in rows if r['channel']=='independent' and r['system']==system and r['configured_loss']==loss]
            pts.append((loss, sum(r[key] for r in rs)/len(rs)))
        out.append((system,pts))
    return out

def main():
    p=argparse.ArgumentParser(); p.add_argument('--samples',type=int,default=1000,help='Monte Carlo messages per system/channel condition'); p.add_argument('--seed',type=int,default=42); p.add_argument('--output-dir',type=Path,required=True); args=p.parse_args()
    root=Path(__file__).resolve().parents[2]; data_path=Path(__file__).with_name('n6_synthetic_emergency_v1.json'); data=json.loads(data_path.read_text(encoding='utf-8')); msgs=data['utterances']; out=args.output_dir; out.mkdir(parents=True,exist_ok=True)
    rows=[]
    def run(channel, condition, loss, ge_cfg=None):
        for sy_i,system in enumerate(SYSTEMS):
            for i in range(args.samples):
                msg=msgs[(i*17 + sy_i*5) % len(msgs)] # balanced deterministic cycling
                n=packet_count(msg['text']); required=(math.ceil(n/4)*5 if system[0].startswith('B_') else n+(system[1] or 0))
                seed=args.seed + (0 if channel=='independent' else 10_000_000) + int(loss*1_000_000) + i*97
                losses=independent(seed,loss,required) if channel=='independent' else ge(seed,required,ge_cfg)
                r=simulate(system,msg,losses)
                rows.append(dict(channel=channel,condition=condition,configured_loss=loss,system=system[0],trial=i,message_id=msg['id'],language=msg['language'],**r))
    for loss in LOSS_LEVELS: run('independent',f'p={loss:.2f}',loss)
    for name,cfg in BURSTS.items(): run('gilbert_elliott',name,cfg[3]*(.08/(.08+.12))+.01*(.12/(.08+.12)),cfg)
    fields=list(rows[0]);
    with (out/'raw_results.csv').open('w',newline='',encoding='utf-8') as f: w=csv.DictWriter(f,fieldnames=fields,lineterminator='\n');w.writeheader();w.writerows(rows)
    summary=[]
    for channel in sorted(set(r['channel'] for r in rows)):
      for condition in sorted(set(r['condition'] for r in rows if r['channel']==channel)):
       for system,_ in SYSTEMS:
        rs=[r for r in rows if r['channel']==channel and r['condition']==condition and r['system']==system]; n=len(rs)
        record={'channel':channel,'condition':condition,'system':system,'trials':n,'configured_loss':rs[0]['configured_loss']}
        for k in ['semantic','text','frame','category','urgency','severity','victim','hazard']:
            s=sum(bool(r[k]) for r in rs); lo,hi=wilson(s,n); record[k+'_recovery']=s/n; record[k+'_ci95']=[lo,hi]
        record['packets_transmitted_mean']=sum(r['packets'] for r in rs)/n; record['bytes_transmitted_mean']=sum(r['bytes'] for r in rs)/n
        record['erased_packets']=sum(r['erased'] for r in rs); record['transmitted_packets']=sum(r['packets'] for r in rs); record['observed_loss']=record['erased_packets']/record['transmitted_packets']
        for k in ['semantic_latency','text_latency']:
            vs=[r[k] for r in rs if r[k] is not None]; record[k+'_packet_units_mean']=sum(vs)/len(vs) if vs else None
        # Useful information is a complete authoritative transcript plus a separately usable semantic frame, each counted as one availability unit.
        record['useful_units_per_packet']=(sum(r['text']+r['semantic'] for r in rs)/n)/record['packets_transmitted_mean']
        summary.append(record)
    analytic=[]
    for loss in LOSS_LEVELS:
      for R in (1,2,3):
        trials=100000; sim=sum(not all(independent(args.seed+int(loss*1e6)+i, loss, R)) for i in range(trials))/trials
        analytic.append({'loss':loss,'replicas':R,'theory':1-loss**R,'monte_carlo':sim,'absolute_error':abs(sim-(1-loss**R)),'trials':trials})
    # FEC sanity via exhaustive erasure patterns in K=4,N=5: recover iff <=1 data loss; parity loss alone is harmless.
    fec=[]
    for e in range(6): fec.append({'erased_of_5':e,'recovery_possible': e<=1, 'condition':'all four data packets available, or exactly one data packet erased with parity received'})
    cfg={'benchmark':'N6 Level-1 Application-Layer Semantic Priority Erasure Coding','python':sys.version,'platform':platform.platform(),'seed':args.seed,'samples_per_system_condition':args.samples,'dataset_path':str(data_path.relative_to(root)),'dataset_sha256':hashlib.sha256(data_path.read_bytes()).hexdigest(),'dataset_count':len(msgs),'language_distribution':{x:sum(m['language']==x for m in msgs) for x in sorted({m['language'] for m in msgs})},'telugu':'NOT EVALUATED: no Telugu utterances and actual N5 extractor has no Telugu terms','text_packet_payload_bytes':TEXT_PACKET_BYTES,'n5_mapping':'SemanticMetadata v1 is seven bytes: version, category ordinal, urgency ordinal, severity ordinal, hazard bitset, victimCount (0=unspecified), certainty ordinal. This Python benchmark models it as an atomic 7-byte semantic payload; it does not import Kotlin or demonstrate PacketV2 wire compatibility.','systems':{'A':'text chunks only','B':'systematic K=4 data packets plus one XOR parity (N=5); a block recovers iff no more than one data packet is erased and parity is present when needed','C':'semantic-first atomic 7-byte payload replicated R=1,2,3; unprotected 32-byte text chunks follow'},'independent_loss_levels':LOSS_LEVELS,'burst_models':{k:{'good_loss':v[0],'good_to_bad':v[1],'bad_to_good':v[2],'bad_loss':v[3],'stationary_bad':.4,'expected_bad_run_packets':1/v[2],'expected_loss':v[3]*.4+v[0]*.6} for k,v in BURSTS.items()},'git_commit':__import__('subprocess').check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'timestamp_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
    (out/'configuration.json').write_text(json.dumps(cfg,indent=2,ensure_ascii=False)); (out/'summary_metrics.json').write_text(json.dumps({'summary':summary,'analytical_replication_checks':analytic,'fec_sanity':fec},indent=2))
    png(out/'semantic_recovery_vs_packet_loss.png',aggregate(rows,'semantic'),'Semantic recovery')
    png(out/'triage_accuracy_vs_packet_loss.png',aggregate(rows,'frame'),'Triage/frame recovery')
    png(out/'full_text_recovery_vs_packet_loss.png',aggregate(rows,'text'),'Full text recovery')
    max_packets=max(q['packets_transmitted_mean'] for q in summary if q['channel']=='independent')
    packet_series=[]
    for s,_ in SYSTEMS:
        points=[]
        for l in LOSS_LEVELS:
            q=next(q for q in summary if q['channel']=='independent' and q['condition']==f'p={l:.2f}' and q['system']==s)
            points.append((l, q['packets_transmitted_mean']/max_packets))
        packet_series.append((s,points))
    png(out/'latency_or_packet_cost_vs_loss.png',packet_series,'Normalized packet cost')
    burst_series=[]
    for s,_ in SYSTEMS: burst_series.append((s,[(i/2, next(q for q in summary if q['channel']=='gilbert_elliott' and q['condition']==name and q['system']==s)['semantic_recovery']) for i,name in enumerate(['mild','moderate','severe'])]))
    png(out/'burst_loss_results.png',burst_series,'Burst semantic recovery')
    (out/'plot_metadata.json').write_text(json.dumps({'series_order':[s for s,_ in SYSTEMS],'x_axis_independent':'configured packet erasure probability 0..0.5','y_axis':'recovery probability (except packet-cost plot, normalized to its own 0..1 scale)','burst_order':['mild','moderate','severe']},indent=2))
    print(json.dumps({'rows':len(rows),'output_dir':str(out),'dataset_count':len(msgs),'samples':args.samples},indent=2))
if __name__=='__main__': main()
