# Ejercicio 3

• ¿Por qué hay que tomar primero el lock del predecesor y después el del nodo actual? Piensen
qué podrı́a pasar si los locks se toman en cualquier órden.
No existe un orden específico. Lo que sí es esencial es mantener un único orden para todas las operaciones del tipo de lista para evitar deadlocks, ya que se se obtienen los locks en orden inverso la situación es insostenible. 

• Cuando se pasa de un nodo a otro, ¿en qué orden hay que liberar y tomar nuevos locks?
Liberás solo el anterior y tomás el siguiente para siempre tener lock de el anterior y siguiente e ir recorriendo de forma ordenada. 

• ¿Hay que bloquear nodos en el método contains?
No, el método contains es wait-free. 

# Ejercicio 4

• ¿Qué hace el método edgeExists?

Dadas referencias a dos nodos pred --> curr, retorna true si sigue existiendo la relación pred --> curr en la lista

• ¿Qué condiciones hay que asegurar sobre nodo.next durante el borrado de un nodo para no
romper otros usuarios concurrentes?

Tenemos que asegurar tener lock de el nodo next porque si el mismo es eliminado durante el borrado del nodo actual la lista queda rota.

• ¿Qué hay que hacer si después de tomar los locks nos damos cuenta que edgeExists(prev,
curr) es false?

Reintentar. 

# Ejercicio 5

•¿Qué representa el campo marked de la clase Node?
Representa si algún proceso concurrente marcó ese nodo para eliminarlo.

• Si tanto marked como next son variables atómicas, ¿por qué sigue haciendo falta un lock por
nodo?
Porque la atomicidad es de una variable a la vez, y para poner eliminar un nodo necesitamos que no esté marcado 
y que además cumpla edgeExists(prev,curr). 

• ¿Qué se necesita para verificar que la relación previo → actual sigua existiendo?
Que ni el previo, actual y next estén marked.

# Ejercicio 6

• ¿Qué hace el método compareAndSet? ¿Qué podrı́a ocasionar que este retorne false?

Compara el valor del campo correspondiente con el esperado. Si son iguales, modifica el campo y pone el valor nuevo
Sino, retorna false.

• ¿En qué se diferencia conceptualmente el tipo AtomicMarkableReference<T> de volatile
T?
Permite modificar las dos variables (marked y next) atómicamente, mientras que volatile las modificaba por separado
de forma atómica.

3• Analizar el método Window find(int value). Luego explicar el rol que cumple la marca en
el campo next de la clase Node.

