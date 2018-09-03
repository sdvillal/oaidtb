:: Ejemplo de uso de los clasificadores desde la l�nea de comandos
:: Se puede utilizar en conjunci�n con el GOE (copiar y pegar la configuraci�n de los clasificadores)
:: Con peque�as modificaciones, con este script se pueden programar experimentos en modo batch
@echo off

SET TRAIN_DATA=..\..\_data_\uci\iris.arff
SET SCHEME_NAME=oaidtb.boosters.AdaBoostECC
SET SCHEME_OPTIONS=-F oaidtb.filters.NominalToRandomPermutationOfEvenSplitOCFilter -U 1 -I 100 -S 1 -N 0 -W weka.classifiers.trees.DecisionStump --

SET MAIN_CLASS_NAME= %SCHEME_NAME% -t %TRAIN_DATA% %SCHEME_OPTIONS%

::SET OAIDTB_OUTPUT_FILE=..\..\_results_\ECC_Iris.txt
SET OAIDTB_OUTPUT_FILE=

call _oaidtb_.bat %OAIDTB_OUTPUT_FILE%