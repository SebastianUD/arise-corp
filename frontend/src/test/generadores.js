export function mulberry32(semilla) {
  let estado = semilla
  return () => {
    estado = (estado + 0x6D2B79F5) >>> 0
    let mezcla = Math.imul(estado ^ (estado >>> 15), estado | 1)
    mezcla ^= mezcla + Math.imul(mezcla ^ (mezcla >>> 7), mezcla | 61)
    return ((mezcla ^ (mezcla >>> 14)) >>> 0) / 4294967296
  }
}
