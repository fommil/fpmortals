package tradetemplate

import scalaz._, Scalaz._

sealed abstract class Currency
case object EUR extends Currency
case object USD extends Currency

final case class TradeTemplate(
  payments: List[java.time.LocalDate],
  ccy: Option[Currency],
  otc: Option[Boolean]
)
object TradeTemplate {
  // implicit private[this] def lastWins[A]: Monoid[Option[A]] = Monoid.instance(
  //   {
  //     case (None, None) => None
  //     case (only, None) => only
  //     case (None, only) => only
  //     case (_, winner)  => winner
  //   },
  //   None
  // )

  implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
    (a, b) =>
      TradeTemplate(a.payments |+| b.payments,
                    b.ccy <+> a.ccy,
                    b.otc <+> a.otc),
    TradeTemplate(Nil, None, None)
  )
}
