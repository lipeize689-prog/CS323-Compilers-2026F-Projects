#include <stdio.h>
#include <stdint.h>
int read(void){int n=0;if(scanf("%d",&n)!=1)return 0;return n;}
int write(int n){printf("%d\n",n);return 0;}
int __spl_pow(int a,int b){int result=1;if(b<0)return 0;while(b){if(b&1)result=(int)((uint32_t)result*(uint32_t)a);a=(int)((uint32_t)a*(uint32_t)a);b>>=1;}return result;}
extern int main0(void);
int main(void){return main0();}
