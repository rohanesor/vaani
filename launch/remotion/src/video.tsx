import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';

/** Evidence-safe motion foundation. Real recordings are intentionally supplied at render time, never recreated here. */
export const LaunchFilm: React.FC<{vertical: boolean}> = ({vertical}) => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 20, vertical ? 1030 : 2230, vertical ? 1050 : 2250], [0, 1, 1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  const copy = frame < 240 ? 'Different languages. One human need.'
    : frame < 480 ? 'VAANI is being developed for more accessible multilingual communication.'
    : frame < 1260 ? 'Validated local product experience'
    : frame < 1710 ? 'Android local UI  •  Server contracts  •  Web experience'
    : 'VAANI\nEvery language. One conversation.';
  return <AbsoluteFill style={{background:'#111111', color:'#F5F0E8', justifyContent:'center', alignItems:'center', padding:vertical ? 90 : 180, opacity, fontFamily:'Arial, sans-serif'}}>
    <div style={{maxWidth: vertical ? 820 : 1300, textAlign:'center', fontWeight:700, fontSize:vertical ? 64 : 92, lineHeight:1.08, whiteSpace:'pre-line'}}>{copy}</div>
    <div style={{position:'absolute', bottom:vertical ? 110 : 70, fontSize:vertical ? 24 : 28, color:'#b8b2a8'}}>Evidence-based launch draft • live provider validation pending</div>
  </AbsoluteFill>;
};
