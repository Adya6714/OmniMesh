const BAR_DURATION_MS = [400, 600, 300, 500];

/** Animated level meters — parity with Android `AcousticVisualizer`. */
export default function AcousticVisualizer() {
  return (
    <div className="acoustic-visualizer" aria-hidden>
      {BAR_DURATION_MS.map((ms) => (
        <span key={ms} className="acoustic-visualizer__bar" style={{ animationDuration: `${ms}ms` }} />
      ))}
    </div>
  );
}
