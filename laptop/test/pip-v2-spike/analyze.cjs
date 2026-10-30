// Pure M0 report guard. Numeric prototype results never establish owner acceptance.
function validStats(value) {
  return Boolean(value && Number.isInteger(value.samples) && value.samples > 0 &&
    ['mean','p95','max'].every(key => Number.isFinite(value[key]) && value[key] >= 0));
}
function analyze(result) {
  const phases = ['idle','drag'];
  const complete = Boolean(result && !result.status && ['A','B'].includes(result.option) &&
    phases.every(key => result[key] && Number.isFinite(result[key].elapsedMs) && result[key].elapsedMs > 0 &&
      validStats(result[key].rendererCpuPercent) && validStats(result[key].allAppProcessesCpuPercent) &&
      validStats(result[key].rendererAnimationFrameIntervalMs)));
  return {complete, pointerStatus:'unknown_owner_check_required', productBudgetStatus:'not_verified_on_target'};
}
module.exports={analyze};
