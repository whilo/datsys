(ns catalysis.shared.utils)

(defn deref-or-value
  [val-or-atom]
  (if (satisfies? IDeref val-or-atom) @val-or-atom val-or-atom))

