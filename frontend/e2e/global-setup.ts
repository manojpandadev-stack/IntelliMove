import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

// Run the repo-scoped test-data isolation before the suite so the Kafka
// auto-dispatch consumer cannot vacuum leftover rides onto an online driver
// mid-test. Best-effort: failures are logged, never fatal.
export default function globalSetup() {
  const script = path.resolve(
    fileURLToPath(new URL('.', import.meta.url)), '..', '..', 'clean-test-data.sh');
  // Windows: plain `bash` may resolve to WSL, where C:\ paths are invisible.
  // Prefer Git for Windows' bash explicitly.
  const candidates = [
    'C:\\Program Files\\Git\\bin\\bash.exe',
    'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
    process.env['ProgramFiles'] ? `${process.env['ProgramFiles']}\\Git\\bin\\bash.exe` : '',
  ].filter((p): p is string => p !== '' && fs.existsSync(p));
  const bash = candidates[0] ?? 'bash';
  const repoDir = path.resolve(fileURLToPath(new URL('.', import.meta.url)), '..', '..');
  try {
    execSync(`"${bash}" "${script}"`, { stdio: 'inherit', cwd: repoDir });
  } catch (err) {
    console.warn('[global-setup] test-data cleanup failed (best-effort):', err);
  }
}

