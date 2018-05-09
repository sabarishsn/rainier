package rainier.sampler

import rainier.compute._

/**
  * The Hamiltonian update algorithm is called leapfrog:
  * we make a half-step to update the ps,
  * a full step to update the qs with the half-updated ps,
  * then another half step to update the ps using the new qs.
  */
private trait HamiltonianIntegrator {
  def step(hParams: HParams, stepSize: Double): HParams
}

private case class LeapFrogIntegrator(
    nVars: Int,
    cf: Array[Double] => (Double, Array[Double]))
    extends HamiltonianIntegrator {

  val stepSize = new Variable
  val potential = new Variable
  val qs = (1 to nVars).map { _ =>
    new Variable
  }
  val ps = (1 to nVars).map { _ =>
    new Variable
  }
  val grad = (1 to nVars).map { _ =>
    new Variable
  }
  val inputs = combineSeq(stepSize, potential, qs, ps, grad)

  def combineSeq[A](stepSize: A,
                    potential: A,
                    qs: Seq[A],
                    ps: Seq[A],
                    grad: Seq[A]): Seq[A] = {
    stepSize +: potential +: (qs ++ ps ++ grad)
  }

  def componentsSeq[A](inputs: Seq[A]): (A, A, Seq[A], Seq[A], Seq[A]) = {
    require(inputs.size == 3 * nVars + 2, "must have 3nVars + 2 elements!")
    (inputs(0),
     inputs(1),
     inputs.slice(2, nVars + 2),
     inputs.slice(nVars + 2, 2 * nVars + 2),
     inputs.slice(2 * nVars + 2, 3 * nVars + 2))
  }

  def componentsArray[A](
      inputs: Array[A]): (A, A, Array[A], Array[A], Array[A]) = {
    require(inputs.size == 3 * nVars + 2, "must have 3nVars + 2 elements!")
    (inputs(0),
     inputs(1),
     inputs.slice(2, nVars + 2),
     inputs.slice(nVars + 2, 2 * nVars + 2),
     inputs.slice(2 * nVars + 2, 3 * nVars + 2))
  }

  private def halfStepPs(inputs: Seq[Variable]) = {
    val (stepSize, potential, qs, ps, grad) = componentsSeq(inputs)
    val newPs = ps
      .zip(grad)
      .map { case (p, grad) => p - (stepSize / 2) * grad }

    combineSeq(stepSize, potential, qs, newPs, grad)
  }

  private def fullStepQs(inputs: Seq[Real]) = {
    val (stepSize, potential, qs, ps, grad) = componentsSeq(inputs)
    val newQs = qs
      .zip(ps)
      .map { case (q, p) => q + (stepSize * p) }
    combineSeq(stepSize, potential, newQs, ps, grad)
  }

  private val output1 = (halfStepPs _ andThen fullStepQs _)(inputs)
  private val cf1 = Compiler.default.compile(inputs, output1)

  private val output2 = halfStepPs(inputs)
  private val cf2 = Compiler.default.compile(inputs, output2)

  private val leapFrogCF: Array[Double] => Array[Double] = { array =>
    val (stepSize, _, newQs, halfNewPs, _) = componentsArray(cf1(array))
    val (potential, grad) = cf(newQs)
    cf2(stepSize +: potential +: (newQs ++ halfNewPs ++ grad))
  }

  def step(hParams: HParams, stepSize: Double): HParams = {
    val inputArray =
      stepSize +: hParams.potential +: (hParams.qs ++ hParams.ps ++ hParams.gradPotential)
    val (_, newPotential, newQs, newPs, newGrad) = componentsArray(
      leapFrogCF(inputArray))
    hParams.copy(
      potential = newPotential,
      qs = newQs,
      ps = newPs,
      gradPotential = newGrad
    )
  }
}
