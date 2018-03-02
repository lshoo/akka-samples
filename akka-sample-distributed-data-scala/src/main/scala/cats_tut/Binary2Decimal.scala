package cats_tut

import java.math.BigInteger

/**
  * Created by liaoshifu on 17/11/14
  */
object Binary2Decimal {

  def binaryToDecTailRecur(src: String, res: Long): Long = {
    new BigInteger(src, 2).toString.toLong
  }

  def main(args: Array[String]): Unit = {
    val src = "1001101100000000000000000000000000000000000000"
    println(src.length)
    val result = binaryToDecTailRecur(src, 0)
    println(result)

    val str = 155
    println(str.toBinaryString)
  }
}
