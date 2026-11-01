export type EditKind = 'typing' | 'paste' | 'bulk' | 'external' | 'undo' | 'redo';

export interface ChangeItem {
  textLen: number;
  rangeLen: number;
}

export interface ClassifyInput {
  reason?: 'undo' | 'redo';
  changes: ChangeItem[];
  isActiveDoc: boolean;
  windowActive: boolean;
}

/**
 * Pure function to classify an editor change event.
 * SPEC DIGEST item 1 rules in strict order:
 * 1. !isActiveDoc or !windowActive -> 'external'
 * 2. reason 'undo' / 'redo' -> 'undo' / 'redo'
 * 3. changes.length === 0 -> 'external'
 * 4. total = sum(max(textLen, rangeLen)); maxLen = max of those
 * 5. total > 2000, or (changes.length > 20 and maxLen > 3) -> 'bulk'
 * 6. exactly 1 change, rangeLen === 0, textLen > 40 -> 'paste'
 * 7. otherwise -> 'typing'
 */
export function classifyEdit(input: ClassifyInput): EditKind {
  if (!input.isActiveDoc || !input.windowActive) {
    return 'external';
  }

  if (input.reason === 'undo') {
    return 'undo';
  }
  if (input.reason === 'redo') {
    return 'redo';
  }

  if (!input.changes || input.changes.length === 0) {
    return 'external';
  }

  let total = 0;
  let maxLen = 0;

  for (const c of input.changes) {
    const itemMax = Math.max(c.textLen, c.rangeLen);
    total += itemMax;
    if (itemMax > maxLen) {
      maxLen = itemMax;
    }
  }

  if (total > 2000 || (input.changes.length > 20 && maxLen > 3)) {
    return 'bulk';
  }

  if (input.changes.length === 1 && input.changes[0].rangeLen === 0 && input.changes[0].textLen > 40) {
    return 'paste';
  }

  return 'typing';
}

/**
 * Checks if a URI scheme is valid for tracking ('file' or 'untitled').
 * Ignores 'output', 'git', 'vscode-userdata', 'comment', etc.
 */
export function isTrackedScheme(scheme?: string): boolean {
  if (!scheme) return false;
  const s = scheme.toLowerCase();
  return s === 'file' || s === 'untitled';
}
