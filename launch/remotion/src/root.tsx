import React from 'react';
import { Composition } from 'remotion';
import { LaunchFilm } from './video';
export const LaunchRoot: React.FC = () => <>
  <Composition id="VaaniLaunchLandscape" component={LaunchFilm} durationInFrames={2250} fps={30} width={1920} height={1080} defaultProps={{ vertical: false }} />
  <Composition id="VaaniLaunchVertical" component={LaunchFilm} durationInFrames={1050} fps={30} width={1080} height={1920} defaultProps={{ vertical: true }} />
</>;
